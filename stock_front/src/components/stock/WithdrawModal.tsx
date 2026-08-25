import React, { useState } from "react";
import {
  Box,
  Button,
  Modal,
  TextField,
  Typography,
} from "@mui/material";
import { withdrawBalance } from "../../api/stockApi";

interface WithdrawModalProps {
  open: boolean;
  onClose: () => void;
  userId: string | null;
  balance: number | null;
  onWithdrawn?: () => void;
}

const WithdrawModal: React.FC<WithdrawModalProps> = ({
  open,
  onClose,
  userId,
  balance,
  onWithdrawn,
}) => {
  const [amount, setAmount] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    const parsed = Number(amount);
    if (!userId) {
      setError("로그인 정보가 없습니다.");
      return;
    }
    if (!amount || parsed <= 0 || Number.isNaN(parsed)) {
      setError("출금 금액은 0보다 큰 숫자여야 합니다.");
      return;
    }
    if (balance !== null && parsed > balance) {
      setError("잔액이 부족합니다.");
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await withdrawBalance(userId, Math.floor(parsed));
      // 성공 시 콜백 (외부에서 잔액 재조회/토스트 처리)
      onWithdrawn?.();
      onClose();
    } catch (err) {
      // 서버(BalanceErrorCode)의 잔액 부족 등 메시지 전달
      setError(
        err instanceof Error
          ? `출금 요청에 실패했습니다: ${err.message}`
          : "출금 요청에 실패했습니다.",
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
          width: 400,
          bgcolor: "background.paper",
          borderRadius: 2,
          boxShadow: 24,
          p: 4,
        }}
      >
        <Typography variant="h6" gutterBottom sx={{ fontWeight: 700 }}>
          출금하기
        </Typography>
        <Typography variant="body2" color="textSecondary" sx={{ mb: 1 }}>
          현재 잔액:{" "}
          <strong>{balance !== null ? balance.toLocaleString() : "-"}</strong>원
        </Typography>
        <TextField
          fullWidth
          type="number"
          label="출금 금액 (원)"
          value={amount}
          onChange={(e) => {
            setAmount(e.target.value);
            setError(null);
          }}
          inputProps={{ min: 1, step: 1 }}
          error={!!error}
          helperText={error || ""}
        />
        <Box sx={{ display: "flex", gap: 1, justifyContent: "flex-end", mt: 2 }}>
          <Button onClick={onClose} color="inherit">
            취소
          </Button>
          <Button
            variant="contained"
            disabled={loading}
            onClick={handleSubmit}
          >
            {loading ? "처리 중..." : "출금 요청"}
          </Button>
        </Box>
      </Box>
    </Modal>
  );
};

export default WithdrawModal;
