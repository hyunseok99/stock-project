import React, { useState } from "react";
import {
  Box,
  Button,
  Modal,
  TextField,
  Typography,
} from "@mui/material";
import { depositBalance } from "../../api/stockApi";

interface DepositModalProps {
  open: boolean;
  onClose: () => void;
  userId: string | null;
  onDeposited?: () => void;
}

const DepositModal: React.FC<DepositModalProps> = ({
  open,
  onClose,
  userId,
  onDeposited,
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
      setError("입금 금액은 0보다 큰 숫자여야 합니다.");
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await depositBalance(userId, Math.floor(parsed));
      // 성공 시 콜백 (외부에서 토스트/스낙바 처리)
      onDeposited?.();
      onClose();
    } catch (err) {
      setError(
        err instanceof Error ? `입금 요청에 실패했습니다: ${err.message}` : "입금 요청에 실패했습니다.",
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
          입금하기
        </Typography>
        <TextField
          fullWidth
          type="number"
          label="입금 금액 (원)"
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
            {loading ? "처리 중..." : "입금 요청"}
          </Button>
        </Box>
      </Box>
    </Modal>
  );
};

export default DepositModal;