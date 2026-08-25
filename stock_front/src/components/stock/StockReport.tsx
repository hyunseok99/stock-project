import React, { useMemo, useState } from "react";
import {
  Box,
  Typography,
  Button,
  Paper,
  Grid,
  Divider,
  IconButton,
} from "@mui/material";
import HistoryIcon from "@mui/icons-material/History";
import type { Stock } from "../../types/stock";
import type { Holding } from "../../api/stockApi";
import HistoryModal from "./HistoryModal";
import "./StockReport.css";

const CHART_COLORS = [
  "#3182f6",
  "#f04452",
  "#ffbb00",
  "#191f28",
  "#8b95a1",
  "#00d084",
];

// 백엔드 /api/portfolio 로 조회한 보유 주식을 표시하는 조회 전용 자산 리포트
interface StockReportProps {
  stockData: Stock[];
  holdings: Holding[];
  /** 현재 원화 잔액 (미조회 시 null) */
  balance?: number | null;
  userId?: string | null;
  onDepositClick?: () => void;
  onWithdrawClick?: () => void;
}

const StockReport: React.FC<StockReportProps> = ({
  stockData,
  holdings,
  balance,
  userId,
  onDepositClick,
  onWithdrawClick,
}) => {
  // 거래 내역 모달 열림 상태 (히스토리 아이콘 클릭 시)
  const [historyOpen, setHistoryOpen] = useState(false);
  // 실시간 데이터와 매칭하여 수익률 계산
  const reportData = useMemo(() => {
    let totalPurchase = 0;
    let totalEvaluation = 0;

    const details = holdings.map((holding, index) => {
      const liveStock = stockData.find((s) => s.ticker === holding.ticker);
      const currentPrice = liveStock
        ? liveStock.endPrice
        : holding.purchasePrice ?? 0;
      const evaluation = currentPrice * holding.quantity;
      const purchaseAmount = holding.purchasePrice * holding.quantity;
      const profit = evaluation - purchaseAmount;
      const profitRate =
        ((currentPrice - holding.purchasePrice) / holding.purchasePrice) * 100;

      totalPurchase += purchaseAmount;
      totalEvaluation += evaluation;

      return {
        ...holding,
        currentPrice,
        evaluation,
        profit,
        profitRate,
        purchaseAmount,
        color: CHART_COLORS[index % CHART_COLORS.length],
      };
    });

    const totalProfit = totalEvaluation - totalPurchase;
    const totalProfitRate =
      totalPurchase > 0 ? (totalProfit / totalPurchase) * 100 : 0;

    return {
      details,
      totalPurchase,
      totalEvaluation,
      totalProfit,
      totalProfitRate,
    };
  }, [holdings, stockData]);

  // 원형 차트를 위한 그라데이션 생성
  const pieChartStyle = useMemo(() => {
    if (reportData.totalEvaluation === 0) return { background: "#eee" };
    let currentPercentage = 0;

    const gradientParts = reportData.details.map((item) => {
      const percentage = (item.evaluation / reportData.totalEvaluation) * 100;
      const start = currentPercentage;
      currentPercentage += percentage;
      return `${item.color} ${start}% ${currentPercentage}%`;
    });
    return { background: `conic-gradient(${gradientParts.join(", ")})` };
  }, [reportData]);

  return (
    <Box className="report-container h-full flex flex-col overflow-hidden">
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 1.5,
        }}
      >
        <Typography className="report-title">내 자산 리포트</Typography>

        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {/* 현재 원화 잔액 */}
          <Typography
            variant="body1"
            className="font-bold text-[#191f28]"
            sx={{ mr: 0.5, whiteSpace: "nowrap" }}
          >
            {balance != null ? `${balance.toLocaleString()}원` : "-"}
          </Typography>

          {/* 출금 버튼 */}
          {onWithdrawClick && (
            <Button
              variant="outlined"
              color="error"
              onClick={onWithdrawClick}
              sx={{
                whiteSpace: "nowrap",
                minWidth: 72,
                height: "40px",
                borderRadius: "12px",
                fontWeight: 600,
                borderColor: "#f04452",
                "&:hover": {
                  borderColor: "#d93a48",
                  backgroundColor: "rgba(240, 68, 82, 0.04)",
                },
              }}
            >
              출금
            </Button>
          )}

          {/* 입금 버튼 */}
          {onDepositClick && (
            <Button
              variant="contained"
              sx={{
                whiteSpace: "nowrap",
                minWidth: 72,
                height: "40px",
                borderRadius: "12px",
                fontWeight: 600,
                backgroundColor: "#3182f6",
                "&:hover": { backgroundColor: "#1b64da" },
              }}
              disableElevation
              onClick={onDepositClick}
            >
              입금
            </Button>
          )}

          {/* 거래 히스토리 아이콘 버튼 */}
          <IconButton
            size="small"
            onClick={() => setHistoryOpen(true)}
            aria-label="거래 내역 보기"
            title="거래 내역"
            sx={{ border: "1px solid #e5e8eb", borderRadius: "10px", color: "#4e5968" }}
          >
            <HistoryIcon fontSize="small" />
          </IconButton>
        </Box>
      </Box>

      <Grid
        container
        spacing={2}
        direction="column"
        wrap="nowrap"
        sx={{ flex: 1, overflow: "hidden" }}
      >
        {/* 위쪽: 요약 및 차트 */}
        <Grid size={{ xs: 12 }}>
          <Paper elevation={0} className="summary-card">
            <Box className="pie-chart-wrapper">
              <div
                className="pie-chart-circle"
                style={{
                  ...pieChartStyle,
                }}
              />
              <Box className="legend-container">
                {[...reportData.details]
                  .sort((a, b) => b.evaluation - a.evaluation)
                  .map((item) => (
                    <Box key={item.ticker} className="legend-item">
                      <div
                        className="legend-color"
                        style={{
                          backgroundColor: item.color,
                        }}
                      />
                      <Typography className="legend-text">
                        {item.name} (
                        {(
                          (item.evaluation /
                            (reportData.totalEvaluation || 1)) *
                          100
                        ).toFixed(1)}
                        %)
                      </Typography>
                    </Box>
                  ))}
              </Box>
            </Box>
            <Box
              className="space-y-2"
              sx={{ width: "100%", maxWidth: "400px" }}
            >
              <div className="flex justify-between">
                <Typography className="summary-label">총 매수금액</Typography>
                <Typography className="summary-value">
                  {reportData.totalPurchase.toLocaleString()}원
                </Typography>
              </div>
              <div className="flex justify-between">
                <Typography className="summary-label">총 평가금액</Typography>
                <Typography className="summary-value">
                  {reportData.totalEvaluation.toLocaleString()}원
                </Typography>
              </div>
              <Divider className="my-2" />
              <div className="flex justify-between">
                <Typography
                  className="summary-value"
                  style={{ fontSize: "19px" }}
                >
                  총 수익
                </Typography>
                <Typography
                  className={`font-bold ${reportData.totalProfit >= 0 ? "text-positive" : "text-negative"}`}
                  style={{ fontSize: "19px" }}
                >
                  {reportData.totalProfit.toLocaleString()}원 (
                  {reportData.totalProfitRate.toFixed(2)}%)
                </Typography>
              </div>
            </Box>
          </Paper>
        </Grid>

        {/* 아래쪽: 보유 주식 리스트 */}
        <Grid
          size={{ xs: 12 }}
          sx={{
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
            flex: 1,
            minHeight: 0,
          }}
        >
          <Box className="scroll-container space-y-3">
            {reportData.details.length === 0 && (
              <Typography className="text-center text-gray-400 py-10">
                보유 주식이 없습니다.
              </Typography>
            )}
            {reportData.details.map((item) => (
              <Paper
                key={item.ticker}
                elevation={0}
                className="holding-card flex items-center"
                style={{
                  borderColor: item.color,
                }}
              >
                {/* 종목명 - 왼쪽 */}
                <Box sx={{ width: "25%" }}>
                  <Typography className="font-bold text-[#191f28]">
                    {item.name}
                  </Typography>
                </Box>

                {/* 총 보유수 - 가운데에서 살짝 왼쪽 */}
                <Box sx={{ width: "25%" }}>
                  <Typography variant="body2" color="textSecondary">
                    총 보유 수: {item.quantity}주
                  </Typography>
                </Box>

                {/* 정보 컨테이너 (총 매수 금액, 평단, 수익률) - 오른쪽 끝 */}
                <Box
                  className="flex flex-col text-right"
                  sx={{ flex: 1, mr: 2 }}
                >
                  <Typography variant="caption" color="textSecondary">
                    총 매수 금액:{" "}
                    {Math.round(item.purchaseAmount).toLocaleString()}원
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    현재 가치: {Math.round(item.evaluation).toLocaleString()}원
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    평단: {Math.round(item.purchasePrice).toLocaleString()}원
                  </Typography>
                  <Typography
                    variant="caption"
                    className={
                      item.profit >= 0 ? "text-positive" : "text-negative"
                    }
                  >
                    수익: {item.profit.toLocaleString()}원 (
                    {item.profitRate.toFixed(2)}%)
                  </Typography>
                </Box>
              </Paper>
            ))}
          </Box>
        </Grid>
      </Grid>

      {/* 거래 내역 모달 (히스토리 아이콘 클릭 시) */}
      <HistoryModal
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
        userId={userId ?? null}
      />
    </Box>
  );
};

export default StockReport;
