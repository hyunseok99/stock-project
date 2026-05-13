import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  CandlestickSeries,
  createChart,
  HistogramSeries,
  ColorType,
} from "lightweight-charts";
import type {
  IChartApi,
  ISeriesApi,
  CandlestickData,
  HistogramData,
  Time,
} from "lightweight-charts";
import { useWebSocket } from "../../contexts/WebSocketContext";
import type { Stock, StockPriceUpdate } from "../../types/stock";
import { fetchStockData } from "../../api/stockApi";
import ChartTypeButton from "./ChartTypeButton";
import type { ChartType } from "./ChartTypeButton";
import "./StockChart.css";

interface StockChartProps {
  ticker: string;
  currentPriceInfo?: Stock | null;
}

interface ExtendedCandlestickData extends CandlestickData<Time> {
  page?: number;
}

interface ExtendedHistogramData extends HistogramData<Time> {
  page?: number;
  color?: string;
}

interface CacheEntry {
  candlestickData: ExtendedCandlestickData[];
  histogramData: ExtendedHistogramData[];
  apiDate?: string; // The actual date of the latest bar from API
  apiVolume?: number; // The volume of the latest bar from API
  currentKafkaDay?: string; // The day of the last Kafka update
  currentKafkaVolume?: number; // The volume of the last Kafka update
}

// Global cache to persist data across component mounts/unmounts while the app is running
const globalDataCache = new Map<string, Map<ChartType, CacheEntry>>();

// --- Static Helpers & Constants (Moved outside to prevent recreation) ---
const intervalRight = { daily: 3, weekly: 3, monthly: 3, yearly: 0 };
const xLabel = { daily: 60, weekly: 60, monthly: 12, yearly: 5 };
const intervalBarSpacing = { daily: 3, weekly: 5, monthly: 5, yearly: 5 };

const normalizeDate = (dateStr: any): Time | null => {
  if (!dateStr) return null;
  // 이미 BusinessDay 객체 형태인 경우 처리
  if (
    typeof dateStr === "object" &&
    dateStr.year &&
    dateStr.month &&
    dateStr.day
  ) {
    return `${dateStr.year}-${String(dateStr.month).padStart(2, "0")}-${String(dateStr.day).padStart(2, "0")}` as Time;
  }

  const date = new Date(dateStr);
  if (isNaN(date.getTime())) {
    if (typeof dateStr === "string" && /^\d{4}-\d{2}-\d{2}$/.test(dateStr))
      return dateStr as Time;
    return null;
  }
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}` as Time;
};

/**
 * 차트 타입에 따라 날짜를 대표 날짜로 정렬합니다.
 * Weekly: 해당 주의 금요일, Monthly: 해당 월의 1일, Yearly: 해당 년의 1월 1일
 */
const getAlignedTime = (dateStr: any, chartType: ChartType): string => {
  const normalized = normalizeDate(dateStr);
  if (!normalized) return "";

  const d = new Date(normalized as string);
  if (chartType === "weekly") {
    const day = d.getUTCDay(); // 0(일) ~ 6(토)
    // 월요일 기준으로 맞춘 후 +4일 (금요일)
    const diff = d.getUTCDate() - day + (day === 0 ? -6 : 1);
    d.setUTCDate(diff + 4);
    return normalizeDate(d.toISOString()) as string;
  } else if (chartType === "monthly") {
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-01`;
  } else if (chartType === "yearly") {
    return `${d.getUTCFullYear()}-01-01`;
  }
  return normalized as string;
};

const getTimeStr = (t: any): string => {
  if (typeof t === "string") return t;
  if (typeof t === "object" && t !== null && "year" in t) {
    return `${t.year}-${String(t.month).padStart(2, "0")}-${String(t.day).padStart(2, "0")}`;
  }
  return String(t);
};

const formatDateForType = (dateStr: string, chartType: ChartType) => {
  const date = new Date(dateStr);
  switch (chartType) {
    case "monthly":
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}` as Time;
    case "yearly":
      return `${date.getFullYear()}` as Time;
    default:
      return dateStr as Time;
  }
};

const deduplicateAndSort = <T extends { time: Time; page?: number }>(
  data: (T | null | undefined)[],
): T[] => {
  const processed = data
    .filter(
      (item): item is T =>
        !!item && item.time !== undefined && item.time !== null,
    )
    .map((item) => ({ ...item, time: getTimeStr(item.time) as Time }));

  const uniqueMap = new Map<string, T>();
  processed.forEach((item) => {
    uniqueMap.set(String(item.time), item);
  });

  return Array.from(uniqueMap.values()).sort((a, b) => {
    return String(a.time).localeCompare(String(b.time));
  });
};

const formatPrice = (price: number) => Math.round(price).toLocaleString();

const formatVolume = (value: number) => {
  if (value >= 1e9) return (value / 1e9).toFixed(1) + "B";
  if (value >= 1e6) return (value / 1e6).toFixed(1) + "M";
  if (value >= 1e3) return (value / 1e3).toFixed(1) + "K";
  return value.toLocaleString();
};

const parseStockData = (item: any) => {
  const close = Number(
    item.endPrice ??
      item.weeklyEndPrice ??
      item.monthlyEndPrice ??
      item.yearlyEndPrice ??
      0,
  );
  const open = Number(
    item.startPrice ??
      item.weeklyStartPrice ??
      item.monthlyStartPrice ??
      item.yearlyStartPrice ??
      close,
  );
  const high = Number(
    item.highPrice ??
      item.weeklyHighPrice ??
      item.monthlyHighPrice ??
      item.yearlyHighPrice ??
      close,
  );
  const low = Number(
    item.lowPrice ??
      item.weeklyLowPrice ??
      item.monthlyLowPrice ??
      item.yearlyLowPrice ??
      close,
  );
  const volume = Number(
    item.volume ??
      item.weeklyVolume ??
      item.monthlyVolume ??
      item.yearlyVolume ??
      0,
  );
  return { open, high, low, close, volume };
};

const StockChart: React.FC<StockChartProps> = ({
  ticker,
  currentPriceInfo,
}) => {
  const { lastMessage } = useWebSocket();
  const [, setCandlestickData] = useState<ExtendedCandlestickData[]>([]);
  const [, setHistogramData] = useState<ExtendedHistogramData[]>([]);
  const [type, setType] = useState<ChartType>("daily");

  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candlestickSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const histogramSeriesRef = useRef<ISeriesApi<"Histogram"> | null>(null);
  const isFetchingRef = useRef(false);
  const pageRef = useRef(1);
  const isInitialLoad = useRef(true); // 초기 마운트 시 setData([]) 호출 방지를 위해 true로 시작
  const hasMoreRef = useRef(true);

  // Keep a ref to currentPriceInfo to use in useEffect without adding it to dependencies (avoiding re-init)
  const currentPriceInfoRef = useRef(currentPriceInfo);
  useEffect(() => {
    currentPriceInfoRef.current = currentPriceInfo;
  }, [currentPriceInfo]);

  const fetchData = useCallback(
    async (page: number, newType: ChartType) => {
      if (newType !== type || page === 1 || !hasMoreRef.current) return;

      try {
        const response = await fetchStockData(ticker, newType, page);
        if (response?.length === 0) {
          console.log(`[FetchData] No more data for ${ticker} at page ${page}`);
          isFetchingRef.current = false;
          hasMoreRef.current = false;
          return;
        }

        // API data is already aggregated for the requested type (weekly, monthly, etc.)
        // Just map it to the chart format.
        const additionalCandleData: ExtendedCandlestickData[] = response
          .map((item): ExtendedCandlestickData | null => {
            const time = getAlignedTime(item.date, newType) as Time;
            if (!time) return null;
            const { open, high, low, close } = parseStockData(item);
            return {
              time,
              open,
              high,
              low,
              close,
              page: page,
            };
          })
          .filter((v): v is ExtendedCandlestickData => v !== null);

        const additionalHistogramData: ExtendedHistogramData[] = response
          .map((item): ExtendedHistogramData | null => {
            const time = getAlignedTime(item.date, newType) as Time;
            if (!time) return null;
            const { open, close, volume } = parseStockData(item);
            return {
              time,
              value: volume,
              color: open <= close ? "#d25357" : "#5d83ee",
              page: page,
            };
          })
          .filter((v): v is ExtendedHistogramData => v !== null);

        console.log(
          `[Pagination] Page ${page} loaded for ${newType}. New Items:`,
          additionalCandleData,
        );

        // Update the global cache first to ensure we merge with the latest real-time data
        const tickerCache =
          globalDataCache.get(ticker) || new Map<ChartType, CacheEntry>();
        const cached = tickerCache.get(newType);

        let newCandleData: ExtendedCandlestickData[] = [];
        let newHistData: ExtendedHistogramData[] = [];

        if (cached) {
          newCandleData = deduplicateAndSort([
            ...additionalCandleData,
            ...cached.candlestickData,
          ]);
          newHistData = deduplicateAndSort([
            ...additionalHistogramData,
            ...cached.histogramData,
          ]);

          tickerCache.set(newType, {
            ...cached,
            candlestickData: newCandleData,
            histogramData: newHistData,
          });
        } else {
          newCandleData = deduplicateAndSort(additionalCandleData);
          newHistData = deduplicateAndSort(additionalHistogramData);
        }

        setCandlestickData(newCandleData);
        setHistogramData(newHistData);

        // Explicitly update the chart instance to reflect the new historical data
        if (candlestickSeriesRef.current) {
          candlestickSeriesRef.current.setData(newCandleData);
        }
        if (histogramSeriesRef.current) {
          histogramSeriesRef.current.setData(newHistData);
        }

        isFetchingRef.current = false;
      } catch (error) {
        isFetchingRef.current = false;
      }
    },
    [ticker, type],
  );

  const handleScroll = useCallback(
    (logicalRange: { from: number; to: number } | null) => {
      if (!logicalRange) return;

      if (
        logicalRange.from < 10 &&
        !isFetchingRef.current &&
        hasMoreRef.current
      ) {
        isFetchingRef.current = true;
        const nextPage = pageRef.current + 1;
        pageRef.current = nextPage;

        setTimeout(() => {
          fetchData(nextPage, type);
        }, 250);
      }
    },
    [type, fetchData],
  );

  const removeExistingLegend = useCallback(() => {
    const existingLegend = containerRef.current?.querySelector(".chart-legend");
    if (existingLegend) existingLegend.remove();
  }, []);

  const loadInitialData = useCallback(
    async (
      ticker: string,
      chartType: ChartType,
    ): Promise<CacheEntry | null> => {
      try {
        const tickerCache =
          globalDataCache.get(ticker) || new Map<ChartType, CacheEntry>();
        if (!globalDataCache.has(ticker)) {
          globalDataCache.set(ticker, tickerCache);
        }

        const cached = tickerCache.get(chartType);
        const hasHistory = cached?.candlestickData.some((d) => d.page === 1);

        // Only fetch if we don't have page 1 data in the cache
        if (!hasHistory) {
          const data = await fetchStockData(ticker, chartType, 1);
          if (data && data.length > 0) {
            const formattedCandle: ExtendedCandlestickData[] = data
              .map((item): ExtendedCandlestickData | null => {
                const time = getAlignedTime(item.date, chartType) as Time;
                if (!time) return null;
                const { open, high, low, close } = parseStockData(item);
                return {
                  time,
                  open,
                  high,
                  low,
                  close,
                  page: 1,
                };
              })
              .filter((v): v is ExtendedCandlestickData => v !== null);

            const formattedHist: ExtendedHistogramData[] = data
              .map((item): ExtendedHistogramData | null => {
                const time = getAlignedTime(item.date, chartType) as Time;
                if (!time) return null;
                const { open, close, volume } = parseStockData(item);
                return {
                  time,
                  value: volume,
                  color: open <= close ? "#d25357" : "#5d83ee",
                  page: 1,
                };
              })
              .filter((v): v is ExtendedHistogramData => v !== null);

            const current = tickerCache.get(chartType) || {
              candlestickData: [],
              histogramData: [],
            };
            const latestBar = data[data.length - 1];

            // [Fix] API 데이터와 실시간(Kafka) 데이터 병합 로직 개선
            // 단순히 배열을 합치면 뒤에 오는 Kafka 데이터가 API 데이터를 완전히 덮어써서,
            // 주/월/년봉의 Open(시가) 정보가 Kafka의 Daily Open으로 손실되는 문제 해결
            const currentCandleMap = new Map(
              current.candlestickData.map((c) => [getTimeStr(c.time), c]),
            );
            const currentHistMap = new Map(
              current.histogramData.map((h) => [getTimeStr(h.time), h]),
            );

            const mergedCandles = formattedCandle.map((apiItem) => {
              const timeKey = getTimeStr(apiItem.time);
              const cachedItem = currentCandleMap.get(timeKey);
              if (cachedItem) {
                currentCandleMap.delete(timeKey);
                return {
                  ...apiItem,
                  high: Math.max(apiItem.high, cachedItem.high),
                  low: Math.min(apiItem.low, cachedItem.low),
                  close: cachedItem.close, // 종가는 최신 실시간 데이터 사용
                  // open은 API 데이터(기간 시작가) 유지
                };
              }
              return apiItem;
            });

            const mergedHists = formattedHist.map((apiItem) => {
              const timeKey = getTimeStr(apiItem.time);
              const cachedItem = currentHistMap.get(timeKey);
              if (cachedItem) {
                currentHistMap.delete(timeKey);
                // Daily는 대체, 그 외(Weekly/Monthly/Yearly)는 누적
                const newValue =
                  chartType === "daily"
                    ? Math.max(apiItem.value, cachedItem.value)
                    : apiItem.value + cachedItem.value;
                return { ...apiItem, value: newValue, color: cachedItem.color };
              }
              return apiItem;
            });

            tickerCache.set(chartType, {
              candlestickData: deduplicateAndSort([
                ...mergedCandles,
                ...Array.from(currentCandleMap.values()),
              ]),
              histogramData: deduplicateAndSort([
                ...mergedHists,
                ...Array.from(currentHistMap.values()),
              ]),
              apiDate: latestBar ? latestBar.date : current.apiDate,
              apiVolume: latestBar
                ? parseStockData(latestBar).volume
                : current.apiVolume,
              currentKafkaDay: undefined,
              currentKafkaVolume: 0,
            });
            console.log(
              `[LoadInitialData] Initialized cache for ${ticker} - ${chartType}`,
              tickerCache.get(chartType),
            );
          }
        }

        const finalCache = tickerCache.get(chartType);
        if (!finalCache || finalCache.candlestickData.length === 0) return null;
        return finalCache;
      } catch (error) {
        console.error("[LoadInitialData] Error:", error);
        return null;
      }
    },
    [],
  );

  // WebSocket을 통한 실시간 데이터 처리 로직
  useEffect(() => {
    if (!lastMessage || (lastMessage as any).ticker !== ticker) return;

    const kafkaData = lastMessage as StockPriceUpdate;
    const rawTime = normalizeDate(
      (kafkaData as any).date || new Date().toISOString(),
    );
    if (!rawTime) return;
    const kafkaTime = rawTime as string;

    const chartTypes: ChartType[] = ["daily", "weekly", "monthly", "yearly"];
    const tickerCache =
      globalDataCache.get(ticker) || new Map<ChartType, CacheEntry>();

    if (!globalDataCache.has(ticker)) globalDataCache.set(ticker, tickerCache);

    chartTypes.forEach((t) => {
      const targetTime = getAlignedTime(kafkaTime, t);
      if (!targetTime) return;

      let cached = tickerCache.get(t) as CacheEntry;
      if (!cached) {
        cached = { candlestickData: [], histogramData: [], apiVolume: 0 };
        tickerCache.set(t, cached);
      }

      const close = Number((kafkaData as any).price || kafkaData.endPrice);
      const open = Number(kafkaData.startPrice || close);
      const high = Number(kafkaData.highPrice || close);
      const low = Number(kafkaData.lowPrice || close);
      const volume = Number((kafkaData as any).volume || 0);

      if (isNaN(close)) return;

      // --- Update Logic ---
      const cIndex = cached.candlestickData.findIndex(
        (item) => getTimeStr(item.time) === targetTime,
      );
      let finalVolume = volume;

      if (cIndex !== -1) {
        const candle = cached.candlestickData[cIndex];

        // 기존 봉 업데이트 (High, Low, Close 갱신)
        candle.high = Math.max(candle.high, high);
        candle.low = Math.min(candle.low, low);
        candle.close = close;

        // Volume 처리: API 기준 날짜와 비교하여 누적 여부 결정
        if (t !== "daily" || (cached.apiDate && kafkaTime > cached.apiDate)) {
          finalVolume = (cached.apiVolume || 0) + volume;
        } else {
          // 같은 날짜(혹은 과거) 데이터인 경우: Kafka에서 오는 누적 볼륨으로 대체
          finalVolume = volume;
        }
        cached.histogramData[cIndex].value = finalVolume;
        cached.histogramData[cIndex].color =
          candle.open <= candle.close ? "#d25357" : "#5d83ee";
      } else {
        // 새로운 기간의 봉 생성
        // 새로운 기간의 봉 생성. 주/월/년 차트의 경우, API에서 제공한 불완전한 현재 기간 봉이 있을 수 있으므로 병합을 시도.
        const baseCandle =
          t !== "daily"
            ? cached.candlestickData.find(
                (c) => getTimeStr(c.time) === targetTime,
              )
            : undefined;

        const openToUse = baseCandle ? baseCandle.open : open;
        const highToUse = baseCandle ? Math.max(baseCandle.high, high) : high;
        const lowToUse = baseCandle ? Math.min(baseCandle.low, low) : low;

        if (t !== "daily" || (cached.apiDate && kafkaTime > cached.apiDate)) {
          const baseVolume = baseCandle
            ? cached.histogramData.find(
                (h) => getTimeStr(h.time) === targetTime,
              )?.value || 0
            : 0;
          finalVolume = baseVolume + volume;
        }

        cached.candlestickData.push({
          time: targetTime as Time,
          open: openToUse,
          high: highToUse,
          low: lowToUse,
          close,
        });
        cached.histogramData.push({
          time: targetTime as Time,
          value: finalVolume,
          color: openToUse <= close ? "#d25357" : "#5d83ee",
        });

        // 새 봉이 생겼으므로 캐시 정렬
        cached.candlestickData = deduplicateAndSort(cached.candlestickData);
        cached.histogramData = deduplicateAndSort(cached.histogramData);
      }

      // 현재 보고 있는 차트 타입과 일치하면 차트 UI 즉시 업데이트
      if (t === type && !isInitialLoad.current) {
        const currentCandle = cached.candlestickData.find(
          (d) => getTimeStr(d.time) === targetTime,
        );
        const currentHist = cached.histogramData.find(
          (d) => getTimeStr(d.time) === targetTime,
        );

        if (currentCandle && candlestickSeriesRef.current) {
          const { page, ...candleUpdate } = currentCandle;
          candlestickSeriesRef.current.update(
            candleUpdate as CandlestickData<Time>,
          );
        }
        if (currentHist && histogramSeriesRef.current) {
          const { page, ...histUpdate } = currentHist;
          histogramSeriesRef.current.update(histUpdate as HistogramData<Time>);
        }
      }
    });
  }, [lastMessage, ticker, type]);

  // [Sync Logic] currentPriceInfo가 변경될 때 차트 데이터 동기화
  // (초기 로딩 이후, 부모 컴포넌트로부터 갱신된 최신 가격 정보를 차트에 반영)
  useEffect(() => {
    if (
      !currentPriceInfo ||
      isInitialLoad.current ||
      !candlestickSeriesRef.current ||
      !histogramSeriesRef.current
    )
      return;

    if (currentPriceInfo.ticker !== ticker) return;

    const tickerCache = globalDataCache.get(ticker);
    const cached = tickerCache?.get(type);

    if (!cached || cached.candlestickData.length === 0) return;

    // 1. 현재 시간(오늘)을 기준으로 차트 타입에 맞는 '타겟 시간' 계산
    const now = new Date();
    const alignedTime = getAlignedTime(now.toISOString(), type) as Time;
    if (!alignedTime) return;

    const lastIdx = cached.candlestickData.length - 1;
    const lastCandle = cached.candlestickData[lastIdx];
    const lastTimeStr = getTimeStr(lastCandle.time);

    let targetCandle: ExtendedCandlestickData;
    let isNewBar = false;

    if (String(alignedTime) === lastTimeStr) {
      // (A) 같은 기간: 업데이트
      targetCandle = {
        ...lastCandle,
        close: currentPriceInfo.endPrice,
        high: Math.max(lastCandle.high, currentPriceInfo.highPrice),
        low: Math.min(lastCandle.low, currentPriceInfo.lowPrice),
      };
    } else if (String(alignedTime) > lastTimeStr) {
      // (B) 새로운 기간: 추가
      isNewBar = true;
      const openPrice =
        (currentPriceInfo as any).startPrice ?? currentPriceInfo.endPrice;
      targetCandle = {
        time: alignedTime,
        open: openPrice,
        high: currentPriceInfo.highPrice,
        low: currentPriceInfo.lowPrice,
        close: currentPriceInfo.endPrice,
      };
    } else {
      return;
    }

    candlestickSeriesRef.current.update(targetCandle);

    if (isNewBar) {
      cached.candlestickData.push(targetCandle);
      cached.candlestickData = deduplicateAndSort(cached.candlestickData);
    } else {
      cached.candlestickData[lastIdx] = targetCandle;
    }

    // 거래량 업데이트 (Daily 차트이거나 새로운 봉이 생성된 경우에만 반영)
    if (
      currentPriceInfo.volume !== undefined &&
      (type === "daily" || isNewBar)
    ) {
      const updatedHist: ExtendedHistogramData = {
        time: alignedTime,
        value: currentPriceInfo.volume,
        color: targetCandle.open <= targetCandle.close ? "#d25357" : "#5d83ee",
      };

      histogramSeriesRef.current.update(updatedHist);

      if (isNewBar) {
        cached.histogramData.push(updatedHist);
        cached.histogramData = deduplicateAndSort(cached.histogramData);
      } else {
        cached.histogramData[cached.histogramData.length - 1] = updatedHist;
      }
    }
  }, [currentPriceInfo, ticker, type]);

  // This main useEffect handles chart creation and data loading when ticker or type changes.
  useEffect(() => {
    let isCancelled = false;

    const init = async () => {
      // 1. Reset state and destroy old chart instance
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }
      removeExistingLegend();
      setCandlestickData([]);
      setHistogramData([]);
      pageRef.current = 1;
      hasMoreRef.current = true;
      isInitialLoad.current = true;

      // 2. Fetch initial data
      const result = await loadInitialData(ticker, type);

      if (isCancelled || !result || !containerRef.current) {
        return;
      }

      // [Sync Logic]
      // If we have latest info from StockList (Dashboard), sync the last candle.
      // This fixes the gap when market is closed (no WS updates) but API data is slightly stale.
      const latestInfo = currentPriceInfoRef.current;
      if (
        latestInfo &&
        latestInfo.ticker === ticker &&
        result.candlestickData.length > 0
      ) {
        const lastIdx = result.candlestickData.length - 1;
        const lastCandle = result.candlestickData[lastIdx];

        // Patch the last candle with the latest known price
        result.candlestickData[lastIdx] = {
          ...lastCandle,
          close: latestInfo.endPrice,
          high: Math.max(lastCandle.high, latestInfo.highPrice),
          low: Math.min(lastCandle.low, latestInfo.lowPrice),
        };

        // For daily chart, we can also sync volume confidently
        if (type === "daily" && latestInfo.volume !== undefined) {
          const histIdx = result.histogramData.findIndex(
            (h) => getTimeStr(h.time) === getTimeStr(lastCandle.time),
          );
          if (histIdx !== -1) {
            result.histogramData[histIdx].value = latestInfo.volume;
            result.histogramData[histIdx].color =
              lastCandle.open <= latestInfo.endPrice ? "#d25357" : "#5d83ee";
          }
        }
      }

      // 3. Create new chart instance with the fetched data
      const chartOptions: any = {
        layout: {
          textColor: "#c1c1c4",
          background: { type: ColorType.Solid, color: "#17171c" },
          panes: {
            separatorColor: "#131722",
            separatorHoverColor: "rgba(255, 0, 0, 0.1)",
            enableResize: false,
          },
        },
        grid: {
          vertLines: { color: "#2c2c35" },
          horzLines: { color: "#2c2c35" },
        },
        height: 450,
        localization: {
          locale: "ko-KR",
          dateFormat:
            type === "yearly"
              ? "yyyy"
              : type === "monthly"
                ? "yyyy-MM"
                : "yyyy-MM-dd",
        },
        timeScale: {
          barSpacing: intervalBarSpacing[type],
          borderColor: "#2c2c35",
        },
        rightPriceScale: {
          borderColor: "#2c2c35",
        },
        crosshair: {
          mode: 0,
        },
        panes: [
          {
            id: "mainPane",
            height: 0.7,
          },
          {
            id: "volumePane",
          },
        ],
      };

      const chart = createChart(containerRef.current, chartOptions);
      chartRef.current = chart;

      const candleSeries = chart.addSeries(
        CandlestickSeries,
        {
          upColor: "#d25357",
          downColor: "#5d83ee",
          borderVisible: false,
          wickUpColor: "#d25357",
          wickDownColor: "#5d83ee",
          priceFormat: { type: "custom", formatter: formatPrice },
        },
        0,
      );
      candlestickSeriesRef.current = candleSeries;

      const histSeries = chart.addSeries(
        HistogramSeries,
        {
          priceFormat: { type: "custom", formatter: formatVolume },
        },
        1,
      );
      histogramSeriesRef.current = histSeries;

      const legend = document.createElement("div");
      legend.className = "chart-legend";
      containerRef.current.appendChild(legend);

      chart.subscribeCrosshairMove((param) => {
        if (!param.time || !param.seriesData.size) {
          legend.innerHTML = "";
          return;
        }
        const candle = param.seriesData.get(
          candleSeries,
        ) as CandlestickData<Time>;
        const volume = param.seriesData.get(histSeries) as HistogramData<Time>;

        if (candle) {
          legend.innerHTML = `
            <div style="display: flex; gap: 10px; flex-wrap: wrap;">
              <span>${formatDateForType(param.time as string, type)}</span>
              <span>시가: <span style="color: #fff">${formatPrice(candle.open)}원</span></span>
              <span>고가: <span style="color: #fff">${formatPrice(candle.high)}원</span></span>
              <span>저가: <span style="color: #fff">${formatPrice(candle.low)}원</span></span>
              <span>종가: <span style="color: #fff">${formatPrice(candle.close)}원</span></span>
              ${volume ? `<span>거래량: <span style="color: #fff">${formatVolume(volume.value)}</span></span>` : ""}
            </div>
          `;
        }
      });

      // 4. Apply data to state and chart, and log it
      console.log(
        `[Chart Type Change] Applying data for ${type}. Total Items: ${result.candlestickData.length}`,
        result.candlestickData,
      );
      candleSeries.setData(result.candlestickData);
      histSeries.setData(result.histogramData);
      setCandlestickData(result.candlestickData);
      setHistogramData(result.histogramData);

      isInitialLoad.current = false;

      const from = result.histogramData.length - xLabel[type];
      const to = result.histogramData.length + intervalRight[type];
      chart.timeScale().setVisibleLogicalRange({ from, to });

      chart.timeScale().subscribeVisibleLogicalRangeChange(handleScroll);
    };

    init();

    return () => {
      isCancelled = true;
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }
    };
  }, [ticker, type, handleScroll, loadInitialData, removeExistingLegend]);

  return (
    <div
      className="chart-container"
      style={{
        borderRadius: 0,
        display: "flex",
        flexDirection: "column",
        height: "520px",
        minHeight: "520px",
        overflow: "hidden",
      }}
    >
      <ChartTypeButton setType={setType} type={type} />
      <div
        style={{
          position: "relative",
          width: "100%",
          height: "450px",
          minHeight: "450px",
        }}
      >
        <div ref={containerRef} style={{ width: "100%", height: "100%" }} />
      </div>
    </div>
  );
};

export default StockChart;
