import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useCallback,
} from "react";
import type { ReactNode } from "react";
import type { WrappedMessage, StockPriceUpdate } from "../types/stock";

interface WebSocketContextType {
  socket: WebSocket | null;
  isConnected: boolean;
  lastMessage: WrappedMessage | null;
  lastSnapshot: StockPriceUpdate[] | null;
  sendMessage: (message: any) => void;
  userId: string | null;
  reconnect: () => void;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(
  undefined,
);

let globalSocket: WebSocket | null = null;

const getOrCreateUserId = (): string => {
  let storedUserId = sessionStorage.getItem("stock_user_id");
  if (!storedUserId) {
    storedUserId = "guest_" + crypto.randomUUID().substring(0, 8);
    sessionStorage.setItem("stock_user_id", storedUserId);
  }
  return storedUserId;
};

export const WebSocketProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<WrappedMessage | null>(null);
  const [lastSnapshot, setLastSnapshot] = useState<StockPriceUpdate[] | null>(
    null,
  );
  const [userId] = useState<string | null>(() => getOrCreateUserId());

  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const isUnmountedRef = useRef(false);
  const lastMessageTimeRef = useRef<number>(Date.now());
  const userIdRef = useRef<string | null>(userId);

  // 소켓 강제 종료 및 즉시 재연결
  const connect = useCallback(() => {
    if (isUnmountedRef.current) return;

    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }

    // 기존 연결 정리
    if (globalSocket) {
      try {
        globalSocket.onopen = null;
        globalSocket.onmessage = null;
        globalSocket.onclose = null;
        globalSocket.onerror = null;
        if (
          globalSocket.readyState === WebSocket.OPEN ||
          globalSocket.readyState === WebSocket.CONNECTING
        ) {
          globalSocket.close();
        }
      } catch (e) {
        console.warn("[WebSocket] Error closing existing socket:", e);
      }
      globalSocket = null;
    }

    const currentUserId = userIdRef.current;
    if (!currentUserId) return;

    const baseUrl = import.meta.env.VITE_WS_URL || "wss://shinhan.me/ws";
    const wsUrl = `${baseUrl}?userId=${currentUserId}`;
    console.log(`[WebSocket] Connecting to ${wsUrl} (attempt #${reconnectAttemptsRef.current})`);

    try {
      const ws = new WebSocket(wsUrl);
      globalSocket = ws;
      setSocket(ws);

      ws.onopen = () => {
        if (isUnmountedRef.current) {
          ws.close();
          return;
        }
        console.log("[WebSocket] Connected successfully");
        setIsConnected(true);
        reconnectAttemptsRef.current = 0;
        lastMessageTimeRef.current = Date.now();
      };

      ws.onmessage = (event) => {
        lastMessageTimeRef.current = Date.now();
        try {
          const data = JSON.parse(event.data);
          // 스냅샷 배열 메시지는 직후(0.5ms 내) 도착하는 장 상태 메시지에 덮어씌워지지 않도록 별도 상태로 분리 보존
          if (Array.isArray(data)) {
            setLastSnapshot(data as StockPriceUpdate[]);
          }
          setLastMessage({
            payload: data,
            timestamp: Date.now(),
          });
        } catch (error) {
          console.error("[WebSocket] Failed to parse message:", error);
        }
      };

      ws.onclose = (event) => {
        console.warn(`[WebSocket] Disconnected (code: ${event.code}, reason: ${event.reason || "none"})`);
        setIsConnected(false);
        globalSocket = null;

        if (!isUnmountedRef.current) {
          // 지수 백오프 자동 재연결: 1s, 2s, 4s ... 최대 10s
          const delay = Math.min(
            10000,
            1000 * Math.pow(1.5, reconnectAttemptsRef.current),
          );
          reconnectAttemptsRef.current += 1;
          console.log(`[WebSocket] Reconnecting in ${(delay / 1000).toFixed(1)}s...`);
          reconnectTimerRef.current = setTimeout(connect, delay);
        }
      };

      ws.onerror = (error) => {
        console.error("[WebSocket] Error event:", error);
      };
    } catch (e) {
      console.error("[WebSocket] Connection creation error:", e);
      if (!isUnmountedRef.current) {
        reconnectTimerRef.current = setTimeout(connect, 3000);
      }
    }
  }, []);

  const manualReconnect = useCallback(() => {
    console.log("[WebSocket] Manual reconnect triggered");
    reconnectAttemptsRef.current = 0;
    connect();
  }, [connect]);

  useEffect(() => {
    isUnmountedRef.current = false;
    userIdRef.current = userId;

    connect();

    // --- 화면 복귀(노트북 덮개 열기 / 탭 활성화 / 절전 모드 복구) 및 네트워크 복구 감지 ---
    const checkAndRecoverConnection = () => {
      if (document.visibilityState === "visible") {
        const isDeadOrClosed =
          !globalSocket ||
          globalSocket.readyState === WebSocket.CLOSED ||
          globalSocket.readyState === WebSocket.CLOSING;
        
        // 절전 모드로 인해 장시간(30초 이상) 패킷이 멈췄거나 소켓이 죽은 경우 즉시 재연결
        const isStale = Date.now() - lastMessageTimeRef.current > 30000;

        if (isDeadOrClosed || isStale) {
          console.log(
            `[WebSocket Recovery] Screen active. (isDead=${isDeadOrClosed}, isStale=${isStale}) -> Reconnecting...`,
          );
          manualReconnect();
        }
      }
    };

    const handleVisibilityChange = () => {
      checkAndRecoverConnection();
    };

    const handleFocus = () => {
      checkAndRecoverConnection();
    };

    const handleOnline = () => {
      console.log("[WebSocket Recovery] Network online event detected -> Reconnecting...");
      manualReconnect();
    };

    const handlePageShow = (event: PageTransitionEvent) => {
      const isDeadOrClosed =
        !globalSocket ||
        globalSocket.readyState === WebSocket.CLOSED ||
        globalSocket.readyState === WebSocket.CLOSING;

      if (event.persisted || isDeadOrClosed) {
        console.log("[WebSocket Recovery] PageShow event -> Reconnecting...");
        manualReconnect();
      }
    };

    const handlePageHide = () => {
      console.log("[WebSocket] PageHide event -> Disconnecting socket");
      if (globalSocket) {
        globalSocket.close();
      }
    };

    // 15초 주기 좀비 소켓(Half-Open) 감지 및 생존 확인
    const heartbeatInterval = setInterval(() => {
      if (document.visibilityState === "visible") {
        const isDead =
          !globalSocket ||
          globalSocket.readyState === WebSocket.CLOSED ||
          globalSocket.readyState === WebSocket.CLOSING;
        
        // 45초 이상 수신이 없으면 좀비 소켓으로 간주하고 재접속
        const isZombie =
          globalSocket?.readyState === WebSocket.OPEN &&
          Date.now() - lastMessageTimeRef.current > 45000;

        if (isDead || isZombie) {
          console.warn(`[WebSocket Heartbeat] Connection lost (isDead=${isDead}, isZombie=${isZombie}) -> Reconnecting...`);
          manualReconnect();
        }
      }
    }, 15000);

    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("focus", handleFocus);
    window.addEventListener("online", handleOnline);
    window.addEventListener("pageshow", handlePageShow);
    window.addEventListener("pagehide", handlePageHide);

    return () => {
      isUnmountedRef.current = true;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      clearInterval(heartbeatInterval);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.removeEventListener("focus", handleFocus);
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("pageshow", handlePageShow);
      window.removeEventListener("pagehide", handlePageHide);
      if (globalSocket) {
        globalSocket.close();
        globalSocket = null;
      }
    };
  }, [connect, manualReconnect]);

  const sendMessage = (message: any) => {
    if (globalSocket && globalSocket.readyState === WebSocket.OPEN) {
      globalSocket.send(JSON.stringify(message));
    }
  };

  return (
    <WebSocketContext.Provider
      value={{
        socket,
        isConnected,
        lastMessage,
        lastSnapshot,
        sendMessage,
        userId,
        reconnect: manualReconnect,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocket = () => {
  const context = useContext(WebSocketContext);
  if (context === undefined) {
    throw new Error("useWebSocket must be used within a WebSocketProvider");
  }
  return context;
};
