import type { OrderNotification } from "../types/stock";

/**
 * 순수 Fetch ReadableStream 기반 SSE(Server-Sent Events) 클라이언트.
 * - `event:` / `data:` 라인을 파싱한다.
 * - 연결 종료 시 지수 백오프로 자동 재연결한다.
 * - `close()` 호출 시 스트림을 안전하게 취소한다.
 */

export interface SseSubscribeOptions<T> {
  /** 구독할 백엔드 유저 ID */
  userId: string;
  /** 최초 연결(connect) 이벤트 수신 시 콜백 */
  onConnect?: () => void;
  /** 이름 있는 이벤트 수신 시 콜백 (예: "order_status") */
  onEvent?: (event: string, data: T) => void;
  /** 파싱/JSON 오류 또는 연결 오류 콜백 */
  onError?: (error: Error) => void;
}

export interface SseSubscription {
  /** 연결을 안전하게 종료한다 */
  close: () => void;
}

/** SSE 관련 오류를 나타내는 클래스 */
export class SseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "SseError";
  }
}

/** SSE 프레임 하나의 행(Row)을 의미하는 공개 타입 */
export interface SseFrame {
  event: string;
  id?: string;
  data: string;
}

/** SSE로 오는 주문 알림 payload 타입 (기본) */
export type TransactionNotification = OrderNotification;

function parseSseBlock(block: string): SseFrame | null {
  const lines = block.split("\n");
  let event = "message";
  let id: string | undefined;
  const dataLines: string[] = [];

  for (const rawLine of lines) {
    const line = rawLine.replace(/\r$/, "");
    if (line === "") continue;
    if (line.startsWith(":")) continue; // 주석

    const colonIndex = line.indexOf(":");
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex);
    const value =
      colonIndex === -1 ? "" : line.slice(colonIndex + 1).replace(/^ /, "");

    if (field === "event") {
      event = value;
    } else if (field === "id") {
      id = value;
    } else if (field === "data") {
      dataLines.push(value);
    }
  }

  return { event, id, data: dataLines.join("\n") };
}

/** 서버가 보낸 SSE 블록 단위를 분리해 콜백으로 전달 */
function handleRawBlock(
  block: string,
  onConnect: (() => void) | undefined,
  onEvent: ((event: string, data: any) => void) | undefined,
  onError: ((error: Error) => void) | undefined,
): void {
  if (!block.trim()) return;
  const frame = parseSseBlock(block);
  if (!frame) return;

  if (frame.event === "connect") {
    onConnect?.();
    return;
  }

  const raw = frame.data;
  if (!raw) return;

  try {
    const parsed: unknown = JSON.parse(raw);
    onEvent?.(frame.event, parsed);
  } catch (err) {
    onError?.(err instanceof Error ? err : new Error(String(err)));
  }
}

/**
 * 백엔드 `GET /api/notification/subscribe/{userId}` SSE 채널을 구독한다.
 * 반환된 핸들이 살아 있는 동안 자동으로 다시 연결을 시도한다.
 */
export function subscribeToNotificationChannel<T = unknown>({
  userId,
  onConnect,
  onEvent,
  onError,
}: SseSubscribeOptions<T>): SseSubscription {
  const options: SseSubscribeOptions<T> = {
    userId,
    onConnect,
    onEvent,
    onError,
  };

  let cancelled = false;

  const baseUrl = import.meta.env.VITE_API_BASE_URL || "";
  const url = `${baseUrl}/api/notification/subscribe/${encodeURIComponent(userId)}`;

  /** 한 번의 SSE 연결 시도를 수행 */
  const connectOnce = async (): Promise<void> => {
    const response = await fetch(url, {
      method: "GET",
      headers: { Accept: "text/event-stream" },
    });

    if (!response.ok || !response.body) {
      throw new SseError(`SSE 연결 실패 (HTTP ${response.status})`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let accumulated = "";

    while (!cancelled) {
      const { value, done } = await reader.read();

      if (done) break;

      if (value) {
        accumulated += decoder.decode(value, { stream: true });

        let boundaryIndex = accumulated.indexOf("\n\n");
        while (boundaryIndex !== -1) {
          const block = accumulated.slice(0, boundaryIndex);
          accumulated = accumulated.slice(boundaryIndex + 2);
          handleRawBlock(block, options.onConnect, options.onEvent, options.onError);
          boundaryIndex = accumulated.indexOf("\n\n");
        }
      }
    }

    if (accumulated.trim()) {
      handleRawBlock(accumulated, options.onConnect, options.onEvent, options.onError);
    }
  };

  const run = async (): Promise<void> => {
    let reconnectDelay = 1000; // 1초부터 시작
    while (!cancelled) {
      try {
        await connectOnce();
        if (cancelled) break;
        reconnectDelay = 1000;
      } catch (err) {
        if (cancelled) break;
        const errorMsg = err instanceof Error ? err : new Error(String(err));
        onError?.(errorMsg);
        if (reconnectDelay < 30000) reconnectDelay *= 2; // 지수 백오프, 최대 30초
      }

      if (!cancelled) {
        await new Promise<void>((resolve) => setTimeout(resolve, reconnectDelay));
      }
    }
  };

  void run();

  return {
    close: () => {
      cancelled = true;
    },
  };
}