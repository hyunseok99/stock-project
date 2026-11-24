import React, { useEffect, useState } from "react";
import {
  Box,
  Button,
  Modal,
  TextField,
  Typography,
} from "@mui/material";
import { createOrder } from "../../api/stockApi";
import type { Stock } from "../../types/stock";

interface SellModalProps {
  open: boolean;
  onClose: () => void;
  stock: Stock | null;
  userId: string | null;
  holdingQuantity: number;
  averagePrice?: number;
  onOrderSubmitted?: () => void;
}

const SellModal: React.FC<SellModalProps> = ({
  open,
  onClose,
  stock,
  userId,
  holdingQuantity,
  averagePrice,
  onOrderSubmitted,
}) => {
  const [quantity, setQuantity] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setQuantity("");
    setError(null);
  }, [open]);

  const parsedQty = Number(quantity);
  const isValidQuantity = Number.isInteger(parsedQty) && parsedQty > 0;
  const proceeds =
    stock && isValidQuantity
      ? Math.floor(stock.endPrice * parsedQty)
      : 0;

  const expectedPnl =
    averagePrice !== undefined && stock && isValidQuantity
      ? proceeds - averagePrice * parsedQty
      : 0;

  const handleSubmit = async () => {
    if (!userId) {
      setError("로그인 정보가 없습니다.");
      return;
    }
    if (!stock) {
      setError("선택된 종목이 없습니다.");
      return;
    }
    if (holdingQuantity <= 0) {
      setError("보유 중인 주식이 없습니다.");
      return;
    }
    if (!isValidQuantity) {
      setError("수량은 1 이상의 정수여야 합니다.");
      return;
    }

    if (parsedQty > holdingQuantity) {
      setError("보유 수량을 초과할 수 없습니다.");
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await createOrder({
        orderId: crypto.randomUUID(),
        userId,
        ticker: stock.ticker,
        orderType: "SELL",
        price: Math.floor(stock.endPrice),
        quantity: parsedQty,
      });
      // 202 응답(요청 접수) - 외부에서 "판매 요청 완료" 안내
      onOrderSubmitted?.();
      onClose();
    } catch (err) {
      setError(
        err instanceof Error
          ? `판매 요청에 실패했습니다: ${err.message}`
          : "판매 요청에 실패했습니다.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose}>
      <Box
        sx={{
          position: "absolute",
          top: "50%",
          left: "50%",
          transform: "translate(-50%, -50%)",
          width: 420,
          bgcolor: "background.paper",
          borderRadius: 2,
          boxShadow: 24,
          p: 4,
        }}
      >
        <Typography variant="h6" gutterBottom sx={{ fontWeight: 700 }}>
          {stock ? `${stock.tickerName} (${stock.ticker}) 매도` : "주식 매도"}
        </Typography>

        {stock && (
          <Typography variant="body2" color="textSecondary">
            현재가: {stock.endPrice.toLocaleString()}원
          </Typography>
        )}

        <Typography variant="body2" color="textSecondary">
          보유 수량: {holdingQuantity.toLocaleString()}주
        </Typography>

        <TextField
          fullWidth
          type="number"
          label="매도 수량 (주)"
          value={quantity}
          onChange={(e) => {
            setQuantity(e.target.value);
            setError(null);
          }}
          inputProps={{ min: 1, max: holdingQuantity, step: 1 }}
          error={!!error}
          helperText={error || ""}
          sx={{ mt: 2 }}
        />

        <Box sx={{ display: "flex", gap: 1, mt: 1 }}>
          {[25, 50, 100].map((percent) => (
            <Button
              key={percent}
              size="small"
              variant="outlined"
              disabled={holdingQuantity <= 0}
              onClick={() => {
                const quickQuantity = Math.max(1, Math.floor((holdingQuantity * percent) / 100));
                setQuantity(String(quickQuantity));
                setError(null);
              }}
            >
              {percent}%
            </Button>
          ))}
        </Box>

        <Typography variant="body2" sx={{ mt: 1 }}>
          예상 매도 대금: <b>{proceeds.toLocaleString()}원</b>
        </Typography>

        {averagePrice !== undefined && (
          <Typography variant="body2" sx={{ mt: 1 }}>
            예상 손익: <b>{expectedPnl.toLocaleString()}원</b>
          </Typography>
        )}

        <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end", mt: 2 }}>
          <Button onClick={onClose} color="inherit">
            취소
          </Button>
          <Button
            variant="contained"
            color="error"
            disabled={loading}
            onClick={handleSubmit}
          >
            {loading ? "처리 중..." : "판매 요청"}
          </Button>
        </Box>
      </Box>
    </Modal>
  );
};

export default SellModal;
