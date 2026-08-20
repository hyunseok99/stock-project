import React, { useEffect, useState, useCallback } from "react";
import { Box, Typography, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import "./StockDashboard.css";
import type {
  Stock,
  KafkaData,
  StockPriceUpdate,
  MarketStatusUpdate,
} from "../../types/stock";
import StockPriceList from "./StockPriceList";
import StockChart from "./StockChart";
import StockReport from "./StockReport";
import type { MyHolding } from "./StockReport";
import { fetchTickers } from "../../api/stockApi";
import { useWebSocket } from "../../contexts/WebSocketContext";

const PAGE_SIZE = 10;

// Note: Sub-components will be implemented separately in follow-up steps.
const StockDashboard: React.FC = () => {
  const { lastMessage } = useWebSocket();
  const [allTickers, setAllTickers] = useState<Stock[]>([]);
  const [stockData, setStockData] = useState<Stock[]>([]);
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);

  // 보유 주식 상태 관리 (localStorage 연동)
  const [myHoldings, setMyHoldings] = useState<MyHolding[]>(() => {
    const saved = localStorage.getItem("myHoldings");
    return saved ? JSON.parse(saved) : [];
  });

  // 데이터가 변경될 때마다 localStorage에 저장
  useEffect(() => {
    localStorage.setItem("myHoldings", JSON.stringify(myHoldings));
  }, [myHoldings]);

  // Initial placeholder for stock selection.
  // This will be triggered from the StockPriceList component.
  const onStockSelect = useCallback((pickedStock: Stock) => {
    setSelectedStock(pickedStock);
  }, []);

  useEffect(() => {
    const init = async () => {
      setLoading(true);
      try {
        const tickersData = await fetchTickers();
        setAllTickers(tickersData);

        if (tickersData.length > 0) {
          const initialData = tickersData.slice(0, PAGE_SIZE);
          setStockData(initialData);
          setHasMore(tickersData.length > PAGE_SIZE);
          setPage(1);

          // 첫 번째 주식을 기본 선택
          if (initialData.length > 0) setSelectedStock(initialData[0]);
        }
      } catch (error) {}
      setLoading(false);
    };
    init();
  }, []);

  // 웹소켓 실시간 가격 업데이트 로직 추가
  useEffect(() => {
    if (!lastMessage || !lastMessage.payload) return;

    const { payload } = lastMessage;

    // 1. 스냅샷(배열) 데이터 수신 시 최초 상태 즉시 초기화 로직
    if (Array.isArray(payload)) {
      const snapshot = payload as StockPriceUpdate[];

      setStockData((prev) =>
        prev.map((item) => {
          const found = snapshot.find((s) => s.ticker === item.ticker);
          if (found) {
            return {
              ...item,
              endPrice: Number((found as any).price || found.endPrice),
              diff: found.diff !== undefined ? Number(found.diff) : item.diff,
              diffRate:
                found.diffRate !== undefined
                  ? Number(found.diffRate)
                  : item.diffRate,
              highPrice: found.highPrice
                ? Number(found.highPrice)
                : item.highPrice,
              lowPrice: found.lowPrice ? Number(found.lowPrice) : item.lowPrice,
              startPrice: (found as any).startPrice
                ? Number((found as any).startPrice)
                : item.startPrice,
              volume:
                (found as any).volume !== undefined
                  ? Number((found as any).volume)
                  : item.volume,
              date: (found as any).date || (item as any).date,
              highlightAnimation: "",
            };
          }
          return item;
        }),
      );

      if (selectedStock) {
        const found = snapshot.find((s) => s.ticker === selectedStock.ticker);
        if (found) {
          setSelectedStock((prev) => {
            if (!prev) return null;
            return {
              ...prev,
              endPrice: Number((found as any).price || found.endPrice),
              diff: found.diff !== undefined ? Number(found.diff) : prev.diff,
              diffRate:
                found.diffRate !== undefined
                  ? Number(found.diffRate)
                  : prev.diffRate,
              highPrice: found.highPrice
                ? Number(found.highPrice)
                : prev.highPrice,
              lowPrice: found.lowPrice ? Number(found.lowPrice) : prev.lowPrice,
              startPrice: (found as any).startPrice
                ? Number((found as any).startPrice)
                : prev.startPrice,
              volume:
                (found as any).volume !== undefined
                  ? Number((found as any).volume)
                  : prev.volume,
              date: (found as any).date || (prev as any).date,
            };
          });
        }
      }
      return;
    }

    const kafkaData: KafkaData = payload;

    if ((kafkaData as MarketStatusUpdate).ticker === "-1") {
      // HomePage와 동일한 시장 상태 처리 로직이 필요하다면 여기에 추가
      return;
    }

    const update = kafkaData as StockPriceUpdate;
    const newPrice = Number((update as any).price || update.endPrice);

    setStockData((prevData) => {
      const index = prevData.findIndex((s) => s.ticker === update.ticker);
      if (index === -1) return prevData;

      const updatedStocks = [...prevData];
      const currentStock = updatedStocks[index];

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
        startPrice: (update as any).startPrice
          ? Number((update as any).startPrice)
          : currentStock.startPrice,
        volume:
          (update as any).volume !== undefined
            ? Number((update as any).volume)
            : currentStock.volume,
        date: (update as any).date || (currentStock as any).date,
        highlightAnimation: animationClass,
      };

      return updatedStocks;
    });

    // 애니메이션 제거
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

    // 선택된 주식 정보 업데이트
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
          startPrice: (update as any).startPrice
            ? Number((update as any).startPrice)
            : prev.startPrice,
          volume:
            (update as any).volume !== undefined
              ? Number((update as any).volume)
              : prev.volume,
          date: (update as any).date || (prev as any).date,
        };
      });
    }

    return () => clearTimeout(timeoutId);
  }, [lastMessage, selectedStock?.ticker]);

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

  return (
    <div className="dashboard-container">
      <div className="dashboard-content">
        <div className="dashboard-left-container">
          <Box
            key={selectedStock?.ticker || "report"}
            className="animate-apple-in"
            sx={{ height: "100%" }}
          >
            {selectedStock ? (
              <div className="report-container h-full flex flex-col overflow-hidden">
                {/* Component stubs will be replaced as we build them. */}
                <Box className="p-6 border-b border-gray-100 flex justify-between items-center mb-4 relative">
                  <Box className="flex flex-col">
                    <div className="flex items-baseline mb-1">
                      <Typography
                        variant="h5"
                        className="font-bold text-[#191f28]"
                      >
                        {selectedStock.tickerName}
                      </Typography>
                      <Typography
                        variant="caption"
                        className="text-gray-500 ml-4"
                      >
                        {selectedStock.ticker}
                      </Typography>
                    </div>
                    <Typography
                      variant="h4"
                      className="font-bold text-[#191f28] mb-1"
                    >
                      {selectedStock.endPrice.toLocaleString()}원
                    </Typography>
                    <Typography
                      variant="body1"
                      className={`font-semibold ${
                        selectedStock.diff > 0
                          ? "text-positive"
                          : selectedStock.diff < 0
                            ? "text-negative"
                            : "text-gray-500"
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

                  <Box className="text-right flex flex-col">
                    <Typography
                      variant="body2"
                      className="font-medium text-gray-600 mb-1"
                    >
                      고가:{" "}
                      <span className="text-[#f04452] font-bold">
                        {selectedStock.highPrice.toLocaleString()}원
                      </span>
                    </Typography>
                    <Typography
                      variant="body2"
                      className="font-medium text-gray-600"
                    >
                      저가:{" "}
                      <span className="text-[#3182f6] font-bold">
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

                <div className="flex-1 overflow-hidden px-4 pb-4">
                  <StockChart
                    ticker={selectedStock.ticker}
                    currentPriceInfo={selectedStock}
                  />
                </div>
              </div>
            ) : (
              <StockReport
                allTickers={allTickers}
                stockData={stockData}
                myHoldings={myHoldings}
                setMyHoldings={setMyHoldings}
              />
            )}
          </Box>
        </div>

        <div className="dashboard-stock-list-container">
          <StockPriceList
            parentStockData={stockData}
            parentSelectedStock={selectedStock}
            onStockSelect={onStockSelect}
            onLoadMore={loadMoreStocks}
            hasMore={hasMore}
            loading={loading}
          />
        </div>
      </div>
    </div>
  );
};

export default StockDashboard;
