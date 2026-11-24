import React, {
  useMemo,
  useState,
  useRef,
  useEffect,
  useCallback,
} from "react";
import StarIcon from "@mui/icons-material/Star";
import StarBorderIcon from "@mui/icons-material/StarBorder";
import "./StockPriceList.css";
import type { Stock } from "../../types/stock";
import {
  Box,
  Button,
  Chip,
  IconButton,
  TextField,
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

const WATCHLIST_KEY = "stock_watchlist_tickers";

const readWatchlist = (): Set<string> => {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(WATCHLIST_KEY) ?? "[]");
    if (Array.isArray(stored)) {
      return new Set(stored.filter((ticker): ticker is string => typeof ticker === "string"));
    }
  } catch {
    // 저장소에 접근할 수 없는 경우에도 목록을 사용할 수 있다.
  }
  return new Set();
};

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
  const [search, setSearch] = useState("");
  const [watchlistOnly, setWatchlistOnly] = useState(false);
  const [watchlist, setWatchlist] = useState(readWatchlist);
  const query = search.trim().toLowerCase();

  const toggleWatchlist = (ticker: string) => {
    const next = new Set(watchlist);
    if (!next.delete(ticker)) next.add(ticker);
    setWatchlist(next);
    try {
      localStorage.setItem(WATCHLIST_KEY, JSON.stringify([...next]));
    } catch {
      // 저장소를 사용할 수 없으면 현재 화면에서만 관심종목을 유지한다.
    }
  };

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

    if (watchlistOnly) {
      currentData = currentData.filter((stock) => watchlist.has(stock.ticker));
    }
    if (query) {
      currentData = currentData.filter(
        (stock) =>
          stock.tickerName?.toLowerCase().includes(query) ||
          stock.ticker.toLowerCase().includes(query),
      );
    }

    // Apply sorting based on filter selection
    if (filterBy === "price") {
      currentData.sort((a, b) => b.endPrice - a.endPrice);
    } else if (filterBy === "diffRate") {
      currentData.sort((a, b) => b.diffRate - a.diffRate);
    } else if (filterBy === "tradingVolume") {
      currentData.sort((a, b) => (b.volume || 0) - (a.volume || 0));
    }

    return currentData;
  }, [parentStockData, tickersFromEnv, filterBy, query, watchlistOnly, watchlist]);

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

      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, px: 2, pt: 1.5 }}>
        <TextField
          fullWidth
          size="small"
          label="종목명 또는 티커 검색"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Chip
          size="small"
          label="전체"
          color={watchlistOnly ? "default" : "primary"}
          aria-pressed={!watchlistOnly}
          onClick={() => setWatchlistOnly(false)}
        />
        <Chip
          size="small"
          label={`관심종목 (${watchlist.size})`}
          color={watchlistOnly ? "primary" : "default"}
          aria-pressed={watchlistOnly}
          onClick={() => setWatchlistOnly(true)}
        />
      </Box>

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
                        {Number(stock.endPrice).toLocaleString()}원
                      </div>
                      <div
                        className={`stock-price-list-stock-diff ${Number(stock.diff) > 0 ? "text-positive" : Number(stock.diff) < 0 ? "text-negative" : ""}`}
                      >
                        {Number(stock.diff) > 0 ? "▲" : Number(stock.diff) < 0 ? "▼" : ""}{" "}
                        {Math.abs(Number(stock.diff)).toLocaleString()} (
                        {stock.diffRate}%)
                      </div>
                    </div>
                  </div>
                  <IconButton
                    size="small"
                    aria-label={`${stock.tickerName || stock.ticker} 관심종목`}
                    aria-pressed={watchlist.has(stock.ticker)}
                    onClick={(event) => {
                      event.stopPropagation();
                      toggleWatchlist(stock.ticker);
                    }}
                    sx={{ ml: 0.5 }}
                  >
                    {watchlist.has(stock.ticker) ? (
                      <StarIcon fontSize="small" color="warning" />
                    ) : (
                      <StarBorderIcon fontSize="small" />
                    )}
                  </IconButton>
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
        {(query || watchlistOnly) && hasMore && !loading && (
          <Box sx={{ textAlign: "center", py: 1 }}>
            <Button size="small" onClick={onLoadMore}>
              종목 더 불러오기
            </Button>
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
