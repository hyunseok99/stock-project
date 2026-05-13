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

export type KafkaData = MarketStatusUpdate | StockPriceUpdate;
