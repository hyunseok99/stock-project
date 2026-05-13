import React, { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";

interface WebSocketContextType {
  socket: WebSocket | null;
  isConnected: boolean;
  lastMessage: any;
  sendMessage: (message: any) => void;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(
  undefined,
);

export const WebSocketProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<any>(null);

  useEffect(() => {
    const wsUrl = import.meta.env.VITE_WS_URL || "wss://shinhan.me/ws";
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log("WebSocket Connected");
      setIsConnected(true);
    };

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      setLastMessage(data);
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
      value={{ socket, isConnected, lastMessage, sendMessage }}
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
