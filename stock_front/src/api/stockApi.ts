import axiosInstance from "./axiosInstance";
import type {
  DepositRequest,
  OrderRequest,
  Stock,
  UserInitRequest,
} from "../types/stock";

export interface StockDataResponse {
  date: string;
  startPrice: number;
  highPrice: number;
  lowPrice: number;
  endPrice: number;
  volume: number;
}

/**
 * Fetches Tickers and Tickers' name data for init.
 * @returns Promise with Stock array.
 */
export const fetchTickers = async (): Promise<Stock[]> => {
  try {
    const response = await axiosInstance.get("/api/livestock/tickers");
    return response.data;
  } catch (error) {
    return [];
  }
};

/**
 * Fetches detailed information for a specific stock ticker.
 * @param ticker - The stock ticker symbol.
 * @returns Promise with stock detail.
 */
export const fetchStockByTicker = async (
  ticker: string,
): Promise<Stock | null> => {
  try {
    const response = await axiosInstance.get(`/api/stock/${ticker}`);
    return response.data;
  } catch (error) {
    return null;
  }
};

/**
 * Fetches stock historical data for chart visualization.
 * @param ticker - The stock ticker symbol.
 * @param type - Chart type (daily, weekly, monthly, yearly).
 * @param page - Page number for pagination.
 * @returns Promise with stock data array.
 */
export const fetchStockData = async (
  ticker: string,
  type: string,
  page: number,
): Promise<StockDataResponse[]> => {
  try {
    const response = await axiosInstance.get("/api/livestock/inform", {
      params: { ticker, type, page },
    });
    return response.data;
  } catch (error) {
    return [];
  }
};

/**
 * Searches for stocks based on a query.
 * @param query - The search term.
 * @returns Promise with an array of matching stocks.
 */
export const searchStocks = async (query: string): Promise<Stock[]> => {
  try {
    const response = await axiosInstance.get("/api/stock/search", {
      params: { query },
    });
    return response.data;
  } catch (error) {
    return [];
  }
};

/**
 * Fetches the user's watchlist.
 * @returns Promise with an array of stocks in the watchlist.
 */
export const fetchWatchlist = async (): Promise<Stock[]> => {
  try {
    // This endpoint assumes the backend identifies the user via the auth token.
    const response = await axiosInstance.get("/api/me/watchlist");
    return response.data;
  } catch (error) {
    return [];
  }
};

/**
 * Adds a stock to the user's watchlist.
 * @param ticker - The stock ticker to add.
 * @returns Promise that resolves on success.
 */
export const addToWatchlist = async (ticker: string): Promise<void> => {
  try {
    await axiosInstance.post(`/api/me/watchlist/${ticker}`);
  } catch (error) {
    throw error;
  }
};

/**
 * Removes a stock from the user's watchlist.
 * @param ticker - The stock ticker to remove.
 * @returns Promise that resolves on success.
 */
export const removeFromWatchlist = async (ticker: string): Promise<void> => {
  try {
    await axiosInstance.delete(`/api/me/watchlist/${ticker}`);
  } catch (error) {
    throw error;
  }
};

/**
 * Initializes a guest user (creates user + seeds initial Redis balance).
 * @param userId - The guest user id.
 * @returns Promise that resolves on success.
 */
export const initUser = async (userId: string): Promise<void> => {
  const payload: UserInitRequest = { userId };
  await axiosInstance.post("/api/users/init", payload);
};

/**
 * Requests a deposit into the user's balance.
 * @param userId - The user id.
 * @param amount - The amount to deposit (won).
 * @returns Promise that resolves on success.
 */
export const depositBalance = async (
  userId: string,
  amount: number,
): Promise<void> => {
  const payload: DepositRequest = { userId, amount };
  await axiosInstance.post("/api/balance/deposit", payload);
};

/**
 * 사용자의 보유 주식 포트폴리오를 백엔드에서 조회한다.
 * @param userId - 조회할 사용자 ID
 * @returns 보유 주식 정보 배열
 */
export interface Holding {
  ticker: string;
  name: string;
  quantity: number;
  purchasePrice: number;
}

export const fetchPortfolio = async (
  userId: string,
): Promise<Holding[]> => {
  try {
    const response = await axiosInstance.get(`/api/portfolio?userId=${userId}`);
    const raw: Array<Partial<Holding>> = response.data ?? [];
    return raw.map((h) => ({
      ticker: h.ticker ?? "",
      name: h.name ?? h.ticker ?? "",
      quantity: h.quantity ?? 0,
      purchasePrice: h.purchasePrice ?? 0,
    }));
  } catch (error) {
    console.error("Failed to fetch portfolio:", error);
    return [];
  }
};

// --- [추가] 원화 잔액 / 출금 / 거래 히스토리 ---

/**
 * 현재 원화 잔액을 조회한다. (Redis 우선, Key Miss 시 MariaDB fallback)
 * @returns 잔액 (조회 실패 시 null)
 */
export const getBalance = async (userId: string): Promise<number | null> => {
  try {
    const response = await axiosInstance.get(`/api/balance`, {
      params: { userId },
    });
    const raw = response.data as { balance?: number };
    return typeof raw?.balance === "number" ? raw.balance : null;
  } catch (error) {
    console.error("Failed to fetch balance:", error);
    return null;
  }
};

/**
 * 출금을 요청한다. (202 수락 응답 - Redis 즉시 반영, DB는 비동기 영속화)
 */
export const withdrawBalance = async (
  userId: string,
  amount: number,
): Promise<void> => {
  await axiosInstance.post("/api/balance/withdraw", { userId, amount });
};

/** 통합 거래 히스토리 항목 */
export interface TradeHistoryItem {
  id: number;
  /** CASH(입출금) / ORDER(주문 체결) */
  source: "CASH" | "ORDER";
  /** DEPOSIT / WITHDRAWAL / BUY / SELL */
  type: "DEPOSIT" | "WITHDRAWAL" | "BUY" | "SELL";
  ticker?: string | null;
  quantity?: number | null;
  price?: number | null;
  amount: number;
  /** 입출금은 COMPLETED 고정 / 주문은 SUCCESS 등 */
  status?: string | null;
  /** ISO-8601 시각 */
  timestamp?: string | null;
}

/**
 * 유저의 입출금 + 주문 체결 통합 거래 히스토리를 최신순으로 조회한다.
 */
export const fetchTradeHistory = async (
  userId: string,
  limit: number = 30,
): Promise<TradeHistoryItem[]> => {
  try {
    const response = await axiosInstance.get(`/api/history`, {
      params: { userId, limit },
    });
    return Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    console.error("Failed to fetch trade history:", error);
    return [];
  }
};

/**
 * Requests a stock order (buy/sell).
 * @param order - The order request payload.
 * @returns Promise that resolves on success.
 */
export const createOrder = async (order: OrderRequest): Promise<void> => {
  await axiosInstance.post("/api/orders", order);
};
