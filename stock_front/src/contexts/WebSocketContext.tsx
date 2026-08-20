import React, { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import type { WrappedMessage } from "../types/stock";

interface WebSocketContextType {
  socket: WebSocket | null;
  isConnected: boolean;
  lastMessage: WrappedMessage | null;
  sendMessage: (message: any) => void;
  userId: string | null;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(
  undefined,
);

export const WebSocketProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<WrappedMessage | null>(null);
  const [userId, setUserId] = useState<string | null>(null);
  useEffect(() => {
    let storedUserId = sessionStorage.getItem("stock_user_id");
    
    if(!storedUserId) {
      storedUserId = "guest_" + crypto.randomUUID().substring(0, 8);
      sessionStorage.setItem("stock_user_id", storedUserId);
    }
    
    setUserId(storedUserId);
    
    const baseUrl = import.meta.env.VITE_WS_URL || "wss://shinhan.me/ws";
    const wsUrl = `${baseUrl}?userId=${storedUserId}`;
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log("WebSocket Connected");
      setIsConnected(true);
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        // 설계에 따른 메시지 래핑: 타임스탬프 부여
        setLastMessage({
          payload: data,
          timestamp: Date.now(),
        });
      } catch (error) {
        console.error("Failed to parse WebSocket message:", error);
      }
    };

    ws.onclose = () => {
      console.log("WebSocket Disconnected");
      setIsConnected(false);
    };

    setSocket(ws);

    return () => {
      if (
        ws.readyState === WebSocket.OPEN ||
        ws.readyState === WebSocket.CONNECTING
      ) {
        ws.close();
      }
    };
  }, []);

  const sendMessage = (message: any) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(message));
    }
  };

  return (
    <WebSocketContext.Provider
      value={{ socket, isConnected, lastMessage, sendMessage, userId }}
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
