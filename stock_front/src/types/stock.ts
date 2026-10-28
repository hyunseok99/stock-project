export interface Stock {
  ticker: string;
  tickerName: string;
  endPrice: number;
  diff: number;
  diffRate: number;
  date?: string;
  volume: number;
  highPrice: number;
  lowPrice: number;
  startPrice: number;
  highlightColor?: string;
  highlightAnimation?: string;
}

export interface MarketStatusUpdate {
  ticker: "-1";
  date: string;
  reason: string;
}

export interface StockPriceUpdate {
  ticker: string;
  endPrice: number;
  diff: number;
  diffRate: number;
  date?: string;
  startPrice?: number;
  lowPrice?: number;
  highPrice?: number;
}

export type KafkaData =
  | MarketStatusUpdate
  | StockPriceUpdate
  | StockPriceUpdate[];

export interface WrappedMessage {
  payload: KafkaData;
  timestamp: number;
}

// --- [추가] 입금 / 주문 / SSE 알림 관련 타입 ---

export interface UserInitRequest {
  userId: string;
}

export interface DepositRequest {
  userId: string;
  amount: number;
}

export interface OrderRequest {
  orderId?: string;
  userId: string;
  ticker: string;
  orderType: "BUY" | "SELL";
  price: number;
  quantity: number;
}

export interface OrderNotification {
  orderId?: string;
  userId: string;
  ticker: string;
  orderType?: string;
  status: string; // "SUCCESS" | "FAILED" ...
  message: string;
  processedPartition?: number | null;
  orderedAt?: string;
  executedAt?: string;
  kafkaLagMs?: number | null;
  totalLatencyMs?: number | null;
}
