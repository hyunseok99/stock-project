import React, { useState, useEffect, useCallback } from "react";
import {
  Container,
  Grid,
  Box,
  Typography,
  Paper,
  IconButton,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import StockPriceList from "../components/stock/StockPriceList";
import StockChart from "../components/stock/StockChart";
import StockReport from "../components/stock/StockReport";
import type { MyHolding } from "../components/stock/StockReport";
import { useWebSocket } from "../contexts/WebSocketContext";
import { fetchTickers } from "../api/stockApi";
import type {
  Stock,
  KafkaData,
  StockPriceUpdate,
  MarketStatusUpdate,
} from "../types/stock";
import "./HomePage.css";

const PAGE_SIZE = 10;

const HomePage: React.FC = () => {
  const { lastMessage } = useWebSocket();
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [allTickers, setAllTickers] = useState<Stock[]>([]);
  const [stockData, setStockData] = useState<Stock[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [marketStatus, setMarketStatus] = useState({
    isOpen: false,
    message: "장 상태를 확인하고 있습니다.",
  });

  // 보유 주식 상태 관리 (localStorage 연동)
  const [myHoldings, setMyHoldings] = useState<MyHolding[]>(() => {
    const saved = localStorage.getItem("myHoldings");
    return saved ? JSON.parse(saved) : [];
  });
  
  // 데이터가 변경될 때마다 localStorage에 저장
  useEffect(() => {
    localStorage.setItem("myHoldings", JSON.stringify(myHoldings));
  }, [myHoldings]);

  // Initial data load
  useEffect(() => {
    const init = async () => {
      setLoading(true);
      try {
        // 1. Fetch all tickers from DB
        const tickersData = await fetchTickers();
        setAllTickers(tickersData);

        // 2. Fetch initial stock info for the first page
        if (tickersData.length > 0) {
          const initialData = tickersData.slice(0, PAGE_SIZE);
          setStockData(initialData);
          setHasMore(tickersData.length > PAGE_SIZE);
          setPage(1);
        }
      } catch (error) {}
      setLoading(false);
    };
    init();
  }, []);

  const loadMoreStocks = useCallback(async () => {
    if (loading || !hasMore) return;

    setLoading(true);

    const start = page * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    const nextTickers = allTickers.slice(start, end);

    if (nextTickers.length === 0) {
      setHasMore(false);
      setLoading(false);
      return;
    }

    setStockData((prev) => [...prev, ...nextTickers]);
    setPage(page + 1);
    setHasMore(allTickers.length > end);
    setLoading(false);
  }, [loading, hasMore, page, allTickers]);
  
  // --- 1. Snapshot 전용 업데이트 (최초 접속 시 리스트 전체 갱신) ---
  
  // Update stock data when lastMessage arrives
  useEffect(() => {
    
    if (!lastMessage || !lastMessage.ticker) return;

    const kafkaData: KafkaData = lastMessage;

    // 시장 상태 업데이트 (ticker가 -1인 경우)
    if (kafkaData.ticker === "-1") {
      const statusUpdate = kafkaData as MarketStatusUpdate;
      setMarketStatus((prev) =>
        prev.message === statusUpdate.reason
          ? prev
          : {
              isOpen: false,
              message: statusUpdate.reason || "장이 닫혀있습니다.",
            },
      );
      return; // 장이 닫힌 메시지는 주가 업데이트 로직을 타지 않음
    } else {
      setMarketStatus((prev) =>
        prev.isOpen
          ? prev
          : { isOpen: true, message: "현재 장 오픈되어 있습니다." },
      );
    }

    // 주가 업데이트를 위한 타입 단언 및 변수 정의
    const update = kafkaData as StockPriceUpdate;
    const newPrice = Number((update as any).price || update.endPrice);

    setStockData((prevData) => {
      const index = prevData.findIndex((s) => s.ticker === update.ticker);
      if (index === -1) return prevData;

      const updatedStocks = [...prevData];
      const currentStock = updatedStocks[index];

      // Determine animation class based on price change
      let animationClass = "";
      if (currentStock.endPrice !== 0 && !isNaN(newPrice)) {
        if (newPrice > currentStock.endPrice) animationClass = "highlight-up";
        else if (newPrice < currentStock.endPrice)
          animationClass = "highlight-down";
      }

      updatedStocks[index] = {
        ...currentStock,
        endPrice: isNaN(newPrice) ? currentStock.endPrice : newPrice,
        diff:
          update.diff !== undefined ? Number(update.diff) : currentStock.diff,
        diffRate:
          update.diffRate !== undefined
            ? Number(update.diffRate)
            : currentStock.diffRate,
        highPrice: update.highPrice
          ? Number(update.highPrice)
          : currentStock.highPrice,
        lowPrice: update.lowPrice
          ? Number(update.lowPrice)
          : currentStock.lowPrice,
        highlightAnimation: animationClass,
      };

      return updatedStocks;
    });

    // Clear animation after 1 second
    const timeoutId = setTimeout(() => {
      setStockData((prevData) => {
        const index = prevData.findIndex((s) => s.ticker === update.ticker);
        if (index === -1) return prevData;
        const updatedStocks = [...prevData];
        updatedStocks[index] = {
          ...updatedStocks[index],
          highlightAnimation: "",
        };
        return updatedStocks;
      });
    }, 1000);

    // Update selected stock if it's the one that changed
    if (selectedStock && selectedStock.ticker === update.ticker) {
      setSelectedStock((prev) => {
        if (!prev) return null;
        return {
          ...prev,
          endPrice: isNaN(newPrice) ? prev.endPrice : newPrice,
          diff: update.diff !== undefined ? Number(update.diff) : prev.diff,
          diffRate:
            update.diffRate !== undefined
              ? Number(update.diffRate)
              : prev.diffRate,
          highPrice: update.highPrice
            ? Number(update.highPrice)
            : prev.highPrice,
          lowPrice: update.lowPrice ? Number(update.lowPrice) : prev.lowPrice,
        };
      });
    }

    return () => clearTimeout(timeoutId);
  }, [lastMessage, selectedStock?.ticker]);

  const handleStockSelect = useCallback((stock: Stock) => {
    setSelectedStock(stock);
  }, []);

  return (
    <Box className="home-page-container">
      <Container maxWidth="xl">
        <Grid
          container
          spacing={3}
          wrap="nowrap"
          className="home-page-header"
          sx={{ minWidth: "1200px" }}
        >
          <Grid size={{ xs: 9 }}>
            <Box>
              <Typography variant="h4" className="home-page-title">
                Market Dashboard
              </Typography>
              <Typography variant="body1" className="home-page-subtitle">
                Real-time stock market tracking
              </Typography>
            </Box>
          </Grid>
          <Grid size={{ xs: 3 }}>
            <Box className="market-status-container">
              <div
                className={`status-dot ${marketStatus.isOpen ? "open" : "closed"}`}
              />
              <Typography className="status-text">
                {marketStatus.message}
              </Typography>
            </Box>
          </Grid>
        </Grid>

        <Grid
          container
          spacing={3}
          wrap="nowrap"
          className="home-page-grid-container"
        >
          {/* Center Section: Chart or Empty State */}
          <Grid size={{ xs: 9 }} sx={{ height: "100%" }}>
            <Box
              key={selectedStock?.ticker || "report"}
              className="animate-apple-in"
              sx={{ height: "100%" }}
            >
              {selectedStock ? (
                <Paper elevation={0} className="home-page-chart-paper">
                  <Box
                    className="home-page-chart-header"
                    sx={{ position: "relative" }}
                  >
                    <Box className="home-page-stock-info-box">
                      <div className="home-page-stock-name-row">
                        <Typography
                          variant="h5"
                          className="home-page-stock-name"
                        >
                          {selectedStock.tickerName}
                        </Typography>
                        <Typography
                          variant="caption"
                          className="home-page-stock-ticker"
                        >
                          {selectedStock.ticker}
                        </Typography>
                      </div>
                      <Typography
                        variant="h4"
                        className="home-page-stock-price"
                      >
                        {selectedStock.endPrice.toLocaleString()}원
                      </Typography>
                      <Typography
                        variant="body1"
                        className={`home-page-stock-diff ${
                          selectedStock.diff > 0
                            ? "text-positive"
                            : selectedStock.diff < 0
                              ? "text-negative"
                              : ""
                        }`}
                      >
                        {selectedStock.diff > 0
                          ? "▲"
                          : selectedStock.diff < 0
                            ? "▼"
                            : ""}{" "}
                        {Math.abs(selectedStock.diff).toLocaleString()}원 (
                        {selectedStock.diffRate}%)
                      </Typography>
                    </Box>

                    <Box className="home-page-high-low-box">
                      <Typography
                        variant="body2"
                        className="home-page-price-label"
                      >
                        고가:{" "}
                        <span className="text-positive font-bold">
                          {selectedStock.highPrice.toLocaleString()}원
                        </span>
                      </Typography>
                      <Typography
                        variant="body2"
                        className="home-page-price-label"
                      >
                        저가:{" "}
                        <span className="text-negative font-bold">
                          {selectedStock.lowPrice.toLocaleString()}원
                        </span>
                      </Typography>
                    </Box>
                    <IconButton
                      onClick={() => setSelectedStock(null)}
                      size="small"
                      sx={{
                        position: "absolute",
                        top: 12,
                        right: 12,
                        color: "#8b95a1",
                      }}
                    >
                      <CloseIcon fontSize="small" />
                    </IconButton>
                  </Box>
                  <Box className="home-page-chart-area">
                    <StockChart
                      ticker={selectedStock.ticker}
                      currentPriceInfo={selectedStock}
                    />
                  </Box>
                </Paper>
              ) : (
                <StockReport
                  allTickers={allTickers}
                  stockData={stockData}
                  myHoldings={myHoldings}
                  setMyHoldings={setMyHoldings}
                />
              )}
            </Box>
          </Grid>

          {/* Right Section: Stock List */}
          <Grid size={{ xs: 3 }} sx={{ height: "100%" }}>
            <StockPriceList
              parentStockData={stockData}
              parentSelectedStock={selectedStock}
              onStockSelect={handleStockSelect}
              onLoadMore={loadMoreStocks}
              hasMore={hasMore}
              loading={loading}
            />
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
};

export default HomePage;
