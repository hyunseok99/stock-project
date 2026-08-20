import { useEffect, useState } from "react";
import {
  subscribeToNotificationChannel,
} from "../api/sse";
import type { SseSubscription } from "../api/sse";
import type { OrderNotification } from "../types/stock";

/**
 * SSE 알림 구독 훅.
 * - userId 기반으로 백엔드 알림 채널을 구독한다.
 * - "order_status" 이벤트 수신 시 lastNotification을 갱신한다.
 * - 컴포넌트 unmount 시 연결을 안전하게 종료한다.
 */
export function useOrderNotifications(
  userId: string | null,
): {
  lastNotification: OrderNotification | null;
  sseConnected: boolean;
} {
  const [lastNotification, setLastNotification] =
    useState<OrderNotification | null>(null);
  const [sseConnected, setSseConnected] = useState(false);

  useEffect(() => {
    if (!userId) return;

    let subscription: SseSubscription | null = null;
    let active = true;

    subscription = subscribeToNotificationChannel<OrderNotification>({
      userId,
      onConnect: () => {
        if (!active) return;
        setSseConnected(true);
      },
      onEvent: (event, data) => {
        if (!active) return;
        if (event === "order_status" && data && typeof data === "object") {
          setLastNotification(data as OrderNotification);
        }
      },
      onError: () => {
        if (active) setSseConnected(false);
      },
    });

    return () => {
      active = false;
      subscription?.close();
    };
  }, [userId]);

  return { lastNotification, sseConnected };
}