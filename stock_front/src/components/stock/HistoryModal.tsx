import React, { useEffect, useState } from "react";
import {
  Box,
  Chip,
  CircularProgress,
  Modal,
  Typography,
} from "@mui/material";
import {
  fetchTradeHistory,
  type TradeHistoryItem,
} from "../../api/stockApi";

interface HistoryModalProps {
  open: boolean;
  onClose: () => void;
  userId: string | null;
}

const TYPE_LABEL: Record<string, string> = {
  DEPOSIT: "입금",
  WITHDRAWAL: "출금",
  BUY: "매수",
  SELL: "매도",
};

const TYPE_COLOR: Record<string, "primary" | "error" | "success" | "warning"> = {
  DEPOSIT: "primary",
  WITHDRAWAL: "error",
  BUY: "success",
  SELL: "warning",
};

/** 금액 부호: 입금/매도는 유입(+), 출금/매수는 유출(-) */
const isPositiveAmount = (type: string) =>
  type === "DEPOSIT" || type === "SELL";

const formatDateTime = (iso?: string | null): string => {
  if (!iso) return "-";
  try {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(
      d.getDate(),
    )} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch {
    return iso;
  }
};

const statusChipColor = (
  status?: string | null,
): "success" | "error" | "default" => {
  if (!status) return "default";
  if (status === "SUCCESS" || status === "COMPLETED") return "success";
  if (status === "FAIL" || status === "FAILED") return "error";
  return "default";
};

const STATUS_LABEL: Record<string, string> = {
  COMPLETED: "완료",
  SUCCESS: "체결",
  FAIL: "실패",
  FAILED: "실패",
  PENDING: "대기",
};

const HistoryModal: React.FC<HistoryModalProps> = ({
  open,
  onClose,
  userId,
}) => {
  const [histories, setHistories] = useState<TradeHistoryItem[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !userId) return;
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      const data = await fetchTradeHistory(userId);
      if (!cancelled) setHistories(data);
      setLoading(false);
    };
    load();

    return () => {
      cancelled = true;
    };
  }, [open, userId]);

  return (
    <Modal open={open} onClose={onClose}>
      <Box
        sx={{
          position: "absolute",
          top: "50%",
          left: "50%",
          transform: "translate(-50%, -50%)",
          width: 480,
          maxHeight: "70vh",
          bgcolor: "background.paper",
          borderRadius: 2,
          boxShadow: 24,
          p: 3,
          display: "flex",
          flexDirection: "column",
        }}
      >
        <Typography variant="h6" gutterBottom sx={{ fontWeight: 700 }}>
          거래 내역
        </Typography>

        {loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
            <CircularProgress size={32} />
          </Box>
        ) : histories.length === 0 ? (
          <Typography className="text-center text-gray-400 py-10">
            거래 내역이 없습니다.
          </Typography>
        ) : (
          <Box sx={{ overflowY: "auto", flex: 1 }}>
            {histories.map((h) => {
              const positive = isPositiveAmount(h.type);
              return (
                <Box
                  key={`${h.source}-${h.id}`}
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    gap: 1.5,
                    py: 1.25,
                    borderBottom: "1px solid #f2f4f6",
                  }}
                >
                  {/* 좌측: 타입 칩 + 설명 */}
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Box sx={{ display: "flex", gap: 0.75, alignItems: "center" }}>
                      <Chip
                        size="small"
                        label={TYPE_LABEL[h.type] ?? h.type}
                        color={TYPE_COLOR[h.type] ?? "default"}
                        sx={{ height: 22, fontSize: 12, fontWeight: 700 }}
                      />
                      {h.source === "ORDER" && h.status && (
                        <Chip
                          size="small"
                          variant="outlined"
                          label={STATUS_LABEL[h.status] ?? h.status}
                          color={statusChipColor(h.status)}
                          sx={{ height: 22, fontSize: 11 }}
                        />
                      )}
                    </Box>
                    <Typography
                      variant="caption"
                      color="textSecondary"
                      noWrap
                      display="block"
                    >
                      {h.ticker
                        ? `${h.ticker} · ${h.quantity ?? 0}주 @ ${(
                            h.price ?? 0
                          ).toLocaleString()}원`
                        : formatDateTime(h.timestamp)}
                    </Typography>
                  </Box>

                  {/* 우측: 금액 + 시각 */}
                  <Box sx={{ textAlign: "right" }}>
                    <Typography
                      className={`font-bold ${
                        positive ? "text-positive" : "text-negative"
                      }`}
                    >
                      {positive ? "+" : "-"}
                      {h.amount.toLocaleString()}원
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      {formatDateTime(h.timestamp)}
                    </Typography>
                  </Box>
                </Box>
              );
            })}
          </Box>
        )}
      </Box>
    </Modal>
  );
};

export default HistoryModal;
