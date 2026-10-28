import React, { useState } from "react";
import {
  Box,
  Button,
  Modal,
  TextField,
  Typography,
} from "@mui/material";
import { createOrder } from "../../api/stockApi";
import type { Stock } from "../../types/stock";

interface BuyModalProps {
  open: boolean;
  onClose: () => void;
  stock: Stock | null;
  userId: string | null;
  onOrderSubmitted?: () => void;
}

const BuyModal: React.FC<BuyModalProps> = ({
  open,
  onClose,
  stock,
  userId,
  onOrderSubmitted,
}) => {
  const [quantity, setQuantity] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const parsedQty = Number(quantity);
  const totalPrice =
    stock && quantity && !Number.isNaN(parsedQty)
      ? Math.floor(stock.endPrice * parsedQty)
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
    if (!Number.isInteger(parsedQty) || parsedQty <= 0) {
      setError("수량은 1 이상의 정수여야 합니다.");
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await createOrder({
        orderId: crypto.randomUUID(),
        userId,
        ticker: stock.ticker,
        orderType: "BUY",
        price: Math.floor(stock.endPrice),
        quantity: parsedQty,
      });
      // 202 응답(요청 접수) - 외부에서 "구매 요청 완료" 안내
      onOrderSubmitted?.();
      onClose();
    } catch (err) {
      setError(
        err instanceof Error
          ? `구매 요청에 실패했습니다: ${err.message}`
          : "구매 요청에 실패했습니다.",
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
          {stock ? `${stock.tickerName} (${stock.ticker}) 매수` : "주식 매수"}
        </Typography>

        {stock && (
          <Typography variant="body2" color="textSecondary">
            현재가: {stock.endPrice.toLocaleString()}원
          </Typography>
        )}

        <TextField
          fullWidth
          type="number"
          label="매수 수량 (주)"
          value={quantity}
          onChange={(e) => {
            setQuantity(e.target.value);
            setError(null);
          }}
          inputProps={{ min: 1, step: 1 }}
          error={!!error}
          helperText={error || ""}
          sx={{ mt: 2 }}
        />

        <Typography variant="body2" sx={{ mt: 1 }}>
          예상 총 결제 금액: <b>{totalPrice.toLocaleString()}원</b>
        </Typography>

        <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end", mt: 2 }}>
          <Button onClick={onClose} color="inherit">
            취소
          </Button>
          <Button variant="contained" disabled={loading} onClick={handleSubmit}>
            {loading ? "처리 중..." : "구매 요청"}
          </Button>
        </Box>
      </Box>
    </Modal>
  );
};

export default BuyModal;