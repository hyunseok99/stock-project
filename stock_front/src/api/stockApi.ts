import axiosInstance from "./axiosInstance";
import type { Stock } from "../types/stock";

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
