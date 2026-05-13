import React, { useState, useMemo } from "react";
import {
  Box,
  Typography,
  TextField,
  Button,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Paper,
  Grid,
  IconButton,
  Divider,
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import type { Stock } from "../../types/stock";
import "./StockReport.css";

const CHART_COLORS = [
  "#3182f6",
  "#f04452",
  "#ffbb00",
  "#191f28",
  "#8b95a1",
  "#00d084",
];

export interface MyHolding {
  ticker: string;
  name: string;
  purchasePrice: number;
  quantity: number;
}

interface StockReportProps {
  allTickers: Stock[];
  stockData: Stock[];
  myHoldings: MyHolding[];
  setMyHoldings: React.Dispatch<React.SetStateAction<MyHolding[]>>;
}

const StockReport: React.FC<StockReportProps> = ({
  allTickers,
  stockData,
  myHoldings,
  setMyHoldings,
}) => {
  const [newTicker, setNewTicker] = useState("");
  const [newPrice, setNewPrice] = useState<number | "">("");
  const [newQty, setNewQty] = useState<number | "">("");
  const [errors, setErrors] = useState({
    ticker: false,
    price: false,
    qty: false,
  });

  const handleAddStock = () => {
    const newErrors = {
      ticker: !newTicker,
      price: newPrice === "" || newPrice <= 0,
      qty: newQty === "" || newQty <= 0,
    };

    setErrors(newErrors);

    // 하나라도 에러가 있으면 중단
    if (Object.values(newErrors).some((error) => error)) {
      if (newErrors.ticker) alert("종목을 선택해주세요.");
      else if (newErrors.price || newErrors.qty)
        alert("매수가와 수량은 0보다 커야 합니다.");
      return;
    }

    const tickerInfo = allTickers.find((t) => t.ticker === newTicker);
    if (!tickerInfo) return;

    setMyHoldings((prev) => {
      const existingIndex = prev.findIndex((h) => h.ticker === newTicker);
      if (existingIndex !== -1) {
        const existing = prev[existingIndex];
        const totalQty = existing.quantity + Number(newQty);
        // 가중 평균 단가 계산: (기존 총액 + 신규 총액) / 전체 수량
        const totalCost =
          existing.purchasePrice * existing.quantity +
          Number(newPrice) * Number(newQty);

        const updated = [...prev];
        updated[existingIndex] = {
          ...existing,
          quantity: totalQty,
          purchasePrice: totalCost / totalQty,
        };
        return updated;
      }
      return [
        ...prev,
        {
          ticker: newTicker,
          name: tickerInfo.tickerName,
          purchasePrice: Number(newPrice),
          quantity: Number(newQty),
        },
      ];
    });

    setNewTicker("");
    setNewPrice("");
    setNewQty("");
    setErrors({
      ticker: false,
      price: false,
      qty: false,
    });
  };

  const handleDelete = (index: number) => {
    setMyHoldings(myHoldings.filter((_, i) => i !== index));
  };

  // 실시간 데이터와 매칭하여 수익률 계산
  const reportData = useMemo(() => {
    let totalPurchase = 0;
    let totalEvaluation = 0;

    const details = myHoldings.map((holding, index) => {
      const liveStock = stockData.find((s) => s.ticker === holding.ticker);
      const currentPrice = liveStock
        ? liveStock.endPrice
        : holding.purchasePrice;
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
  }, [myHoldings, stockData]);

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
      <Typography className="report-title">내 자산 리포트</Typography>

      <Grid
        container
        spacing={2}
        direction="column"
        wrap="nowrap"
        sx={{ flex: 1, overflow: "hidden" }}
      >
        {/* 왼쪽: 요약 및 차트 */}
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

        {/* 아래쪽: 추가 및 리스트 */}
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
          <Box className="input-section">
            <FormControl
              size="small"
              sx={{ minWidth: 200 }}
              error={errors.ticker}
            >
              <InputLabel>종목 선택</InputLabel>
              <Select
                value={newTicker}
                label="종목 선택"
                onChange={(e) => {
                  setNewTicker(e.target.value);
                  setErrors((prev) => ({ ...prev, ticker: false }));
                }}
              >
                {allTickers.map((t) => (
                  <MenuItem key={t.ticker} value={t.ticker}>
                    {t.tickerName}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              size="small"
              label="매수가"
              type="number"
              value={newPrice}
              sx={{ flex: 1 }}
              error={errors.price}
              helperText={errors.price ? "금액 확인" : ""}
              onChange={(e) => {
                const val = e.target.value === "" ? "" : Number(e.target.value);
                setNewPrice(val);
                if (val !== "" && val > 0)
                  setErrors((prev) => ({ ...prev, price: false }));
              }}
            />
            <TextField
              size="small"
              label="수량"
              type="number"
              value={newQty}
              sx={{ flex: 1 }}
              error={errors.qty}
              helperText={errors.qty ? "수량 확인" : ""}
              onChange={(e) => {
                const val = e.target.value === "" ? "" : Number(e.target.value);
                setNewQty(val);
                if (val !== "" && val > 0)
                  setErrors((prev) => ({ ...prev, qty: false }));
              }}
            />
            <Button
              variant="contained"
              onClick={handleAddStock}
              className="apple-button"
              disableElevation
            >
              <AddIcon />
            </Button>
          </Box>

          <Box className="scroll-container space-y-3">
            {reportData.details.length === 0 && (
              <Typography className="text-center text-gray-400 py-10">
                보유 주식을 추가해보세요.
              </Typography>
            )}
            {reportData.details.map((item, index) => (
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

                <IconButton size="small" onClick={() => handleDelete(index)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Paper>
            ))}
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
};

export default StockReport;
