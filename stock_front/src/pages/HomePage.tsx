import {
  Container,
  Grid,
  Box,
  Typography,
  Paper,
  IconButton,
  Snackbar,
  Alert,
  Button,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useState, useEffect, useCallback, useRef } from "react";
import React from "react";
import StockPriceList from "../components/stock/StockPriceList";
import StockChart from "../components/stock/StockChart";
import StockReport from "../components/stock/StockReport";
import DepositModal from "../components/stock/DepositModal";
import BuyModal from "../components/stock/BuyModal";
import { useWebSocket } from "../contexts/WebSocketContext";
import { fetchTickers, initUser, fetchPortfolio, getBalance } from "../api/stockApi";
import { useOrderNotifications } from "../hooks/useOrderNotifications";
import WithdrawModal from "../components/stock/WithdrawModal";
import type { Holding } from "../api/stockApi";
import type {
  Stock,
  KafkaData,
  StockPriceUpdate,
  MarketStatusUpdate,
} from "../types/stock";
import "./HomePage.css";

const PAGE_SIZE = 10;

const HomePage: React.FC = () => {
  const { lastMessage, userId } = useWebSocket();
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

  // 입금 / 출금 / 구매 모달 및 알림 상태
  const [depositOpen, setDepositOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [buyOpen, setBuyOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{
    message: string;
    severity: "success" | "error" | "info";
  } | null>(null);

  // 게스트 초기화 (DB + Redis 초기 잔고 워밍) - 1회
  const initUserDone = useRef(false);

  // SSE 구독 훅
  const { lastNotification } = useOrderNotifications(userId);

  // SSE 체결 알림 수신 시 스낵바 표시
  useEffect(() => {
    if (!lastNotification) return;

    const s = (lastNotification.status ?? "").toUpperCase();
    const isSuccess = !s.includes("FAIL");
    setSnackbar({
      severity: isSuccess ? "success" : "error",
      message: isSuccess
        ? `구매 완료! ${lastNotification.ticker} ${lastNotification.message ?? ""}`
        : `구매 실패 - ${lastNotification.message ?? "알 수 없는 이유"}`,
    });
  }, [lastNotification]);

  // T1 설계: 로딩 지연 및 레이스 컨디션 해결을 위한 Ref
  const isTickersLoaded = useRef(false);
  const snapshotBuffer = useRef<StockPriceUpdate[] | null>(null);

    // 보유 주식 상태 관리 (백엔드 API 기반)
  const [holdings, setHoldings] = useState<Holding[]>([]);

  // 원화 잔액 상태 관리 (백엔드 API 기반: Redis 우선 조회)
  const [balance, setBalance] = useState<number | null>(null);

  // 잔액 재조회 함수
  const refetchBalance = useCallback(async () => {
    if (!userId) return;
    const value = await getBalance(userId);
    setBalance(value);
  }, [userId]);

  // 포트폴리오 재조회 함수
  const refetchPortfolio = useCallback(async () => {
    if (!userId) return;
    try {
      const data = await fetchPortfolio(userId);
      setHoldings(data);
    } catch (err) {
      console.error("Failed to fetch portfolio:", err);
    }
  }, [userId]);

  // SSE 체결 알림 수신 시 포트폴리오/잔액 재조회
  // - status 값 형식과 무관하게 항상 동기화 (서버 기준 최신 상태 보장)
  // - order-process 쪽 user_stocks/users 반영이 알림보다 늦을 수 있어
  //   즉시 1회 + 1.5초 후 1회 재시도로 커버
  useEffect(() => {
    if (!lastNotification) return;

    refetchPortfolio();
    refetchBalance();

    const retryTimer = setTimeout(() => {
      refetchPortfolio();
      refetchBalance();
    }, 1500);

    return () => clearTimeout(retryTimer);
  }, [lastNotification, refetchPortfolio, refetchBalance]);

  // Initial data load + guest init
  useEffect(() => {
    const init = async () => {
      setLoading(true);
      try {
                // 0. 게스트 유저 초기화 (DB + Redis 초기 잔고 워밍) - 1회
        if (userId && !initUserDone.current) {
          initUserDone.current = true;
          try {
            await initUser(userId);
          } catch (err) {
            console.error("Guest init error:", err);
          }
        }

        // 0-1. 보유 주식 포트폴리오 조회 (백엔드 API)
        if (userId) {
          try {
            await refetchPortfolio();
          } catch (err) {
            console.error("Portfolio fetch error:", err);
          }

          // 0-2. 현재 원화 잔액 조회 (백엔드 API: Redis 우선)
          try {
            await refetchBalance();
          } catch (err) {
            console.error("Balance fetch error:", err);
          }
        }

        // 1. Fetch all tickers from DB
        const tickersFromDB = await fetchTickers();
        let finalData = tickersFromDB;

        // 2. T1 설계: 지연 병합 (Deferred Merge)
        // 로딩 완료 전에 도착한 스냅샷이 있다면 즉시 병합
        if (snapshotBuffer.current) {
          console.log("[Deferred Merge] Applying buffered snapshot to DB data");
          finalData = tickersFromDB.map((ticker: Stock) => {
            const live = snapshotBuffer.current?.find(
              (s) => s.ticker === ticker.ticker,
            );
            if (live) {
              return {
                ...ticker,
                endPrice: (live as any).price || live.endPrice,
                diff: live.diff,
                diffRate: live.diffRate,
                highPrice: live.highPrice || ticker.highPrice,
                lowPrice: live.lowPrice || ticker.lowPrice,
                startPrice: (live as any).startPrice || ticker.startPrice,
                volume: (live as any).volume || ticker.volume,
                date: (live as any).date || (ticker as any).date,
              };
            }
            return ticker;
          });
          snapshotBuffer.current = null; // 병합 후 버퍼 비우기
        }

        setAllTickers(finalData);

        // 3. Fetch initial stock info for the first page
        if (finalData.length > 0) {
          const initialData = finalData.slice(0, PAGE_SIZE);
          setStockData(initialData);
          setHasMore(finalData.length > PAGE_SIZE);
          setPage(1);
        }

        isTickersLoaded.current = true; // 로딩 완료 플래그 설정
      } catch (error) {
        console.error("Failed to load initial tickers:", error);
      }
      setLoading(false);
    };
    init();
  }, [userId]);

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

  // Update stock data when lastMessage arrives
  useEffect(() => {
    if (!lastMessage) return;

    const { payload } = lastMessage;

    // 1. Snapshot 전용 업데이트 (최초 접속 시 리스트 전체 갱신)
    if (Array.isArray(payload)) {
      const snapshot = payload as StockPriceUpdate[];

      // T1 설계: 로딩 전이라면 버퍼에 저장하고 종료
      if (!isTickersLoaded.current) {
        console.log("[Snapshot Buffer] DB not loaded yet. Buffering snapshot.");
        snapshotBuffer.current = snapshot;
        return;
      }

      // allTickers 업데이트 (검색 및 이후 로딩을 위해 전체 리스트 동기화)
      setAllTickers((prev) =>
        prev.map((ticker) => {
          const found = snapshot.find((s) => s.ticker === ticker.ticker);
          if (found) {
            return {
              ...ticker,
              endPrice: (found as any).price || found.endPrice,
              diff: found.diff,
              diffRate: found.diffRate,
              highPrice: found.highPrice || ticker.highPrice,
              lowPrice: found.lowPrice || ticker.lowPrice,
              startPrice: (found as any).startPrice || ticker.startPrice,
              volume: (found as any).volume || ticker.volume,
              date: (found as any).date || (ticker as any).date,
            };
          }
          return ticker;
        }),
      );

      // stockData 업데이트 (현재 UI에 표시되는 리스트 동기화, 애니메이션 없음)
      setStockData((prev) =>
        prev.map((item) => {
          const found = snapshot.find((s) => s.ticker === item.ticker);
          if (found) {
            return {
              ...item,
              endPrice: (found as any).price || found.endPrice,
              diff: found.diff,
              diffRate: found.diffRate,
              highPrice: found.highPrice || item.highPrice,
              lowPrice: found.lowPrice || item.lowPrice,
              startPrice: (found as any).startPrice || item.startPrice,
              volume: (found as any).volume || item.volume,
              date: (found as any).date || (item as any).date,
              highlightAnimation: "",
            };
          }
          return item;
        }),
      );

      // selectedStock 업데이트
      if (selectedStock) {
        const found = snapshot.find((s) => s.ticker === selectedStock.ticker);
        if (found) {
          setSelectedStock((prev) => {
            if (!prev) return null;
            return {
              ...prev,
              endPrice: (found as any).price || found.endPrice,
              diff: found.diff,
              diffRate: found.diffRate,
              highPrice: found.highPrice || prev.highPrice,
              lowPrice: found.lowPrice || prev.lowPrice,
              startPrice: (found as any).startPrice || prev.startPrice,
              volume: (found as any).volume || prev.volume,
              date: (found as any).date || (prev as any).date,
            };
          });
        }
      }
      return;
    }

    // T1 설계: 로딩 완료 전 단일 업데이트는 무시하거나 버퍼링할 수 있으나,
    // 여기서는 스냅샷을 통한 전체 동기화가 우선이므로 스킵
    if (!isTickersLoaded.current) return;

    // 2. 단일 업데이트 처리
    const kafkaData: KafkaData = payload;

    // 시장 상태 업데이트 (ticker가 -1인 경우)
    if ((kafkaData as MarketStatusUpdate).ticker === "-1") {
      const statusUpdate = kafkaData as MarketStatusUpdate;
      setMarketStatus((prev) =>
        prev.message === statusUpdate.reason
          ? prev
          : {
              isOpen: false,
              message: statusUpdate.reason || "장이 닫혀있습니다.",
            },
      );
      return;
    } else {
      setMarketStatus((prev) =>
        prev.isOpen
          ? prev
          : { isOpen: true, message: "현재 장 오픈되어 있습니다." },
      );
    }

    const update = kafkaData as StockPriceUpdate;
    if (!update.ticker) return;

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

  const handleStockSelect = useCallback((stock: Stock) => {
    setSelectedStock(stock);
  }, []);

  return (
    <Box className="home-page-container">
      <Container maxWidth="xl">
        <Box
          className="home-page-header"
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 2,
            mb: 3,
            mt: 1,
            paddingLeft: 1,
          }}
        >
          <Box sx={{ flex: "1 1 auto", minWidth: "250px" }}>
            <Typography variant="h4" className="home-page-title">
              Market Dashboard
            </Typography>
            <Typography variant="body1" className="home-page-subtitle">
              Real-time stock market tracking
            </Typography>
          </Box>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 2,
              flexWrap: "nowrap",
              flexShrink: 0,
            }}
          >
            <Box
              className="market-status-container"
              sx={{
                px: 3,
                py: 1.5,
                height: "48px",
                width: "auto",
                display: "flex",
                alignItems: "center",
                borderRadius: "14px",
                border: "1px solid #e0e0e0",
                boxShadow: "0 2px 8px rgba(0, 0, 0, 0.04)",
                backgroundColor: "#ffffff",
                boxSizing: "border-box",
                gap: 1.5,
              }}
            >
              <div
                className={`status-dot ${marketStatus.isOpen ? "open" : "closed"}`}
                style={{ flexShrink: 0 }}
              />
              <Typography className="status-text" sx={{ whiteSpace: "nowrap" }}>
                {marketStatus.message}
              </Typography>
            </Box>
          </Box>
        </Box>

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

                    <Box
                      sx={{
                        display: "flex",
                        justifyContent: "flex-end",
                        mt: -1,
                      }}
                    >
                      <Button
                        variant="contained"
                        color="primary"
                        onClick={() => setBuyOpen(true)}
                        sx={{
                          whiteSpace: "nowrap",
                          minWidth: 84,
                          position: "absolute",
                          top: 48,
                          right: 12,
                        }}
                      >
                        구매
                      </Button>
                    </Box>
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
                  stockData={stockData}
                  holdings={holdings}
                  balance={balance}
                  userId={userId}
                  onDepositClick={() => setDepositOpen(true)}
                  onWithdrawClick={() => setWithdrawOpen(true)}
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

        {/* 입금 / 출금 / 구매 모달 */}
        <DepositModal
          open={depositOpen}
          onClose={() => setDepositOpen(false)}
          userId={userId}
          onDeposited={() => {
            setSnackbar({ severity: "success", message: "입금이 완료되었습니다." });
            // Redis에 즉시 반영되므로 202 수신 후 잔액 재조회로 최신화
            refetchBalance();
          }}
        />

        <WithdrawModal
          open={withdrawOpen}
          onClose={() => setWithdrawOpen(false)}
          userId={userId}
          balance={balance}
          onWithdrawn={() => {
            setSnackbar({ severity: "success", message: "출금이 완료되었습니다." });
            // Redis에 즉시 반영되므로 202 수신 후 잔액 재조회로 최신화
            refetchBalance();
          }}
        />

        <BuyModal
          open={buyOpen}
          onClose={() => setBuyOpen(false)}
          stock={selectedStock}
          userId={userId}
          onOrderSubmitted={() =>
            setSnackbar({ severity: "info", message: "구매 요청이 접수되었습니다. 체결 결과를 기다려주세요." })
          }
        />

        {/* 알림 Snackbar */}
        <Snackbar
          open={!!snackbar}
          autoHideDuration={5000}
          onClose={() => setSnackbar(null)}
          anchorOrigin={{ vertical: "top", horizontal: "center" }}
        >
          <Alert
            onClose={() => setSnackbar(null)}
            severity={snackbar?.severity ?? "info"}
            variant="filled"
            sx={{ width: "100%" }}
          >
            {snackbar?.message}
          </Alert>
        </Snackbar>
      </Container>
    </Box>
  );
};

export default HomePage;
