import React, {
  useMemo,
  useState,
  useRef,
  useEffect,
  useCallback,
} from "react";
import "./StockPriceList.css";
import type { Stock } from "../../types/stock";
import {
  Box,
  Select,
  MenuItem,
  FormControl,
  List,
  ListItem,
  Typography,
  CircularProgress,
} from "@mui/material";
import type { SelectChangeEvent } from "@mui/material";

interface StockPriceListProps {
  parentStockData: Stock[];
  parentSelectedStock: Stock | null;
  onStockSelect: (stock: Stock) => void;
  onLoadMore: () => void;
  hasMore: boolean;
  loading: boolean;
}

const StockPriceList: React.FC<StockPriceListProps> = ({
  parentStockData,
  parentSelectedStock,
  onStockSelect,
  onLoadMore,
  hasMore,
  loading,
}) => {
  // Ticker list from .env variable, defaults to empty array if not present.
  const tickersFromEnv = useMemo(() => {
    const envTickers = import.meta.env.VITE_STOCK_TICKERS || "";
    return envTickers ? envTickers.split(",").map((t: string) => t.trim()) : [];
  }, []);

  const [filterBy, setFilterBy] = useState<string>("diffRate");
  const listContainerRef = useRef<HTMLDivElement>(null);

  const handleScroll = useCallback(() => {
    const container = listContainerRef.current;
    if (container) {
      const { scrollTop, scrollHeight, clientHeight } = container;
      // Load more when the user is 200px from the bottom
      if (
        scrollHeight - scrollTop - clientHeight < 200 &&
        hasMore &&
        !loading
      ) {
        onLoadMore();
      }
    }
  }, [onLoadMore, hasMore, loading]);

  useEffect(() => {
    const container = listContainerRef.current;
    container?.addEventListener("scroll", handleScroll);
    return () => container?.removeEventListener("scroll", handleScroll);
  }, [handleScroll]);

  // Filter and sort stockData based on tickers, search term, and filter selection
  const filteredStockData = useMemo(() => {
    let currentData = [...parentStockData];

    // 1. Filter by environment tickers if provided
    if (tickersFromEnv.length > 0) {
      currentData = currentData.filter((stock) =>
        tickersFromEnv.includes(stock.ticker),
      );
    }

    // 3. Apply sorting based on filter selection
    if (filterBy === "price") {
      currentData.sort((a, b) => b.endPrice - a.endPrice);
    } else if (filterBy === "diffRate") {
      currentData.sort((a, b) => b.diffRate - a.diffRate);
    } else if (filterBy === "tradingVolume") {
      currentData.sort((a, b) => (b.volume || 0) - (a.volume || 0));
    }

    return currentData;
  }, [parentStockData, tickersFromEnv, filterBy]);

  const handleFilterChange = (event: SelectChangeEvent<string>) => {
    setFilterBy(event.target.value);
  };

  return (
    <div className="stock-price-list-container">
      <div className="stock-price-list-chart-header">
        <div className="stock-price-list-realtime">Stock Market</div>
        <div className="stock-price-list-filter-select-wrapper">
          <FormControl size="small" fullWidth>
            <Select
              value={filterBy}
              onChange={handleFilterChange}
              displayEmpty
              className="stock-price-list-filter-select"
              variant="outlined"
              sx={{
                ".MuiOutlinedInput-notchedOutline": { border: "none" },
                "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
                  border: "none",
                },
                backgroundColor: "transparent",
                boxShadow: "none",
              }}
            >
              <MenuItem value="diffRate">Change</MenuItem>
              <MenuItem value="price">Price</MenuItem>
              <MenuItem value="tradingVolume">Volume</MenuItem>
            </Select>
          </FormControl>
        </div>
      </div>

      <div ref={listContainerRef} className="stock-price-list-stock-list">
        {filteredStockData.length > 0 ? (
          <List disablePadding>
            {filteredStockData.map((stock, index) => (
              <React.Fragment key={stock.ticker}>
                <ListItem
                  disablePadding
                  className={`stock-price-list-stock-item ${stock.highlightAnimation || ""} ${parentSelectedStock?.ticker === stock.ticker ? "selected" : ""}`}
                  onClick={() => onStockSelect(stock)}
                >
                  <div className="stock-price-list-stock-index">
                    {index + 1}
                  </div>
                  <div className="stock-price-list-stock-content">
                    <div className="stock-price-list-stock-name">
                      {stock.tickerName}{" "}
                      <span
                        style={{
                          fontSize: "12px",
                          color: "#999",
                          fontWeight: "normal",
                          marginLeft: "4px",
                        }}
                      >
                        {stock.ticker}
                      </span>
                    </div>

                    <div className="stock-price-list-stock-price-container">
                      <div className="stock-price-list-stock-price">
                        {stock.endPrice.toLocaleString()}원
                      </div>
                      <div
                        className={`stock-price-list-stock-diff ${stock.diff > 0 ? "text-positive" : stock.diff < 0 ? "text-negative" : ""}`}
                      >
                        {stock.diff > 0 ? "▲" : stock.diff < 0 ? "▼" : ""}{" "}
                        {Math.abs(stock.diff).toLocaleString()} (
                        {stock.diffRate}%)
                      </div>
                    </div>
                  </div>
                </ListItem>
              </React.Fragment>
            ))}
          </List>
        ) : (
          <Box className="p-8 text-center">
            <Typography variant="body2" color="textSecondary">
              No stocks available.
            </Typography>
          </Box>
        )}
        {loading && (
          <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
            <CircularProgress size={24} />
          </Box>
        )}
      </div>
    </div>
  );
};

export default StockPriceList;
