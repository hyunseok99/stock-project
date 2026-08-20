package org.team4.sol_server.domain.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.team4.sol_server.domain.stock.dto.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class LiveStockService {

    private final LiveStockRepository liveStockRepository;
    /*
    *   daily이면 end_date에서 100일 전까지 데이터를 가져오기
    *   weekly이면 end_date에서 1년 전까지 데이터를 가져오기
    *   monthly이면 end_date에서 5년 전까지 데이터를 가져오기
    *   yearly이면 end_date에서 10년 전까지 데이터를 가져오기
    */


    // 초기화를 위한 가장 최신의 데이터를 티커별로 반환
    @Cacheable(value = "initStockDataCache", cacheManager = "localCacheManager")
    public List<DefaultStockDTO> getInitStockData() {
        List<LiveStock> liveStocks = liveStockRepository.findAllLatestStocks();
        List<DefaultStockDTO> defaultData = liveStocks.stream()
            .filter(Objects::nonNull)
            .map(liveStock -> DefaultStockDTO.builder()
                    .ticker(liveStock.getTicker())
                    .tickerName(liveStock.getTickerName())
                    .startPrice(liveStock.getStartPrice())
                    .endPrice(liveStock.getEndPrice())
                    .highPrice(liveStock.getHighPrice())
                    .lowPrice(liveStock.getLowPrice())
                    .diffRate(liveStock.getDiffRate())
                    .diff((int)(liveStock.getStartPrice() * liveStock.getDiffRate() / 100))
                    .volume(liveStock.getVolume())
                    .build())
            .collect(Collectors.toList());

        return defaultData;
    }

    // 캐싱 (어제까지의 데이터)
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul") // 매일 차임 8시에
    @CacheEvict(value = "initStockDataCache", allEntries = true, cacheManager = "localCacheManager")
    public void clearStockDataCache() {
        System.out.println("장 시작 전: 주식 초기 데이터 캐시 초기화 완료!");
    }

    public List<DailyStockDTO> getDailyStockData(String ticker, int page) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(page * 120);
        LocalDate endDate = today.minusDays((page - 1) * 120);

        // 티커 및 조건에 해당하는 일별 데이터 조회
        List<LiveStock> dailyData = liveStockRepository.findByTickerAndDateRangeExclusive(ticker, startDate, endDate);

        // 만약 데이터가 없다면 빈 리스트를 반환
        if (dailyData.isEmpty()) {
            return Collections.emptyList();  // 빈 리스트 반환
        }

        return dailyData.stream()
                    .sorted(Comparator.comparing(LiveStock::getDate)) // 날짜순 정렬
                    .map(liveStock -> DailyStockDTO.builder()
                            .ticker(liveStock.getTicker())
                            .date(liveStock.getDate().toString())  // LocalDate -> String 변환
                            .startPrice(liveStock.getStartPrice())
                            .endPrice(liveStock.getEndPrice())
                            .highPrice(liveStock.getHighPrice())
                            .lowPrice(liveStock.getLowPrice())
                            .volume(liveStock.getVolume())
                            .build())
                    .collect(Collectors.toList());
    }

    public List<WeeklyStockDTO> getWeeklyStockData(String ticker, int page) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(page);
        LocalDate endDate = today.minusYears(page - 1);


        return convertToWeekly(ticker, startDate, endDate);
    }

    public List<MonthlyStockDTO> getMonthlyStockData(String ticker, int page) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(page * 5);
        LocalDate endDate = today.minusYears((page - 1) * 5);

        return convertToMonthly(ticker, startDate, endDate);
    }

    public List<YearlyStockDTO> getYearlyStockData(String ticker, int page){

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(page * 10);
        LocalDate endDate = today.minusYears((page - 1) * 10);

        return convertToYearly(ticker, startDate, endDate);
    }

    // 일별 데이터를 주별로 변환
    private List<WeeklyStockDTO> convertToWeekly(String ticker, LocalDate startDate, LocalDate endDate) {
        // 티커에 해당하는 일별 데이터 조회
        List<LiveStock> dailyData =  liveStockRepository.findByTickerAndDateRangeExclusive(ticker, startDate, endDate);

        // 만약 데이터가 없다면 빈 리스트를 반환
        if (dailyData.isEmpty()) {
            return Collections.emptyList();  // 빈 리스트 반환
        }

        // 주별로 데이터를 그룹화하여 변환
        Map<String, List<LiveStock>> groupedByWeek = dailyData.stream()
            .collect(Collectors.groupingBy(liveStock -> {
                LocalDate date = liveStock.getDate();
                WeekFields weekFields = WeekFields.of(Locale.getDefault()); // 기본 로케일
                int weekOfYear = date.get(weekFields.weekOfYear()); // 주 번호
                // 금요일로 주 시작일 계산 (금요일로 시작일 설정)
                LocalDate startOfWeek = date.with(weekFields.dayOfWeek(), 6);
                // 연도-월-주 시작일 형식
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return startOfWeek.format(formatter);
            }));
        List<WeeklyStockDTO> weeklyDataList = new ArrayList<>();
        // 주별로 데이터 처리 및 DTO 생성
        for (Map.Entry<String, List<LiveStock>> entry : groupedByWeek.entrySet()) {
            String weekDateEntry = entry.getKey();
            List<LiveStock> weekData = entry.getValue();

            // 주별 시가, 종가, 고가, 저가, 거래량 계산
            int weeklyStartPrice = weekData.get(0).getStartPrice();
            int weeklyEndPrice = weekData.get(weekData.size() - 1).getEndPrice();
            int weeklyHighPrice = weekData.stream().mapToInt(LiveStock::getHighPrice).max().orElse(0);
            int weeklyLowPrice = weekData.stream().mapToInt(LiveStock::getLowPrice).min().orElse(0);
            long weeklyVolume = weekData.stream().mapToLong(LiveStock::getVolume).sum();

            // WeeklyStockDTO 객체 빌더 패턴으로 생성
            WeeklyStockDTO weeklyStockDTO = WeeklyStockDTO.builder()
                    .ticker(ticker)
                    .date(weekDateEntry)
                    .weeklyStartPrice(weeklyStartPrice)
                    .weeklyLowPrice(weeklyLowPrice)
                    .weeklyHighPrice(weeklyHighPrice)
                    .weeklyEndPrice(weeklyEndPrice)
                    .weeklyVolume(weeklyVolume)
                    .build();

            // 주별 데이터 리스트에 추가
            weeklyDataList.add(weeklyStockDTO);
        }

        // weekDateEntry로 정렬 (LocalDate로 변환 후 정렬)
        weeklyDataList.sort(Comparator.comparing(weeklyStockDTO -> LocalDate.parse(weeklyStockDTO.getDate())));

        return weeklyDataList;
    }

    // 일별 데이터를 월별로 변환
    private List<MonthlyStockDTO> convertToMonthly(String ticker, LocalDate startDate, LocalDate endDate) {
        // 티커에 해당하는 일별 데이터 조회
        List<LiveStock> dailyData =  liveStockRepository.findByTickerAndDateRangeExclusive(ticker, startDate, endDate);

        // 만약 데이터가 없다면 빈 리스트를 반환
        if (dailyData.isEmpty()) {
            return Collections.emptyList();  // 빈 리스트 반환
        }

        // 월별로 데이터를 그룹화하여 변환
        Map<String, List<LiveStock>> groupedByMonth = dailyData.stream()
                .collect(Collectors.groupingBy(liveStock -> {
                    LocalDate date = liveStock.getDate();
                    return date.getYear() + "-" + (date.getMonthValue()); // 연도-월
                }));

        List<MonthlyStockDTO> monthlyDataList = new ArrayList<>();

        // 월별로 데이터 처리 및 DTO 생성
        for (Map.Entry<String, List<LiveStock>> entry : groupedByMonth.entrySet()) {
            String monthlyDateEntry = entry.getKey();
            List<LiveStock> monthData = entry.getValue();

            // 월별 시가, 종가, 고가, 저가, 거래량 계산
            int monthlyStartPrice = monthData.get(0).getStartPrice();
            int monthlyEndPrice = monthData.get(monthData.size() - 1).getEndPrice();
            int monthlyHighPrice = monthData.stream().mapToInt(LiveStock::getHighPrice).max().orElse(0);
            int monthlyLowPrice = monthData.stream().mapToInt(LiveStock::getLowPrice).min().orElse(0);
            long monthlyVolume = monthData.stream().mapToLong(LiveStock::getVolume).sum();

            // 첫번째 일자 dd를 찾아서 날짜로 설정
            String formattedDate = monthData.get(0).getDate().toString();

            // MonthlyStockDTO 객체 빌더 패턴으로 생성
            MonthlyStockDTO monthlyStockDTO = MonthlyStockDTO.builder()
                    .ticker(ticker)
                    .date(formattedDate)
                    .monthlyStartPrice(monthlyStartPrice)
                    .monthlyLowPrice(monthlyLowPrice)
                    .monthlyHighPrice(monthlyHighPrice)
                    .monthlyEndPrice(monthlyEndPrice)
                    .monthlyVolume(monthlyVolume)
                    .build();

            // 월별 데이터 리스트에 추가
            monthlyDataList.add(monthlyStockDTO);
        }

        monthlyDataList.sort(Comparator.comparing(monthlyStockDTO -> monthlyStockDTO.getDate()));
        return monthlyDataList;
    }

    // 연별 데이터를 연별로 변환
    private List<YearlyStockDTO> convertToYearly(String ticker, LocalDate startDate, LocalDate endDate) {
        // 티커에 해당하는 일별 데이터 조회
        List<LiveStock> dailyData =   liveStockRepository.findByTickerAndDateRangeExclusive(ticker, startDate, endDate);

        // 만약 데이터가 없다면 빈 리스트를 반환
        if (dailyData.isEmpty()) {
            return Collections.emptyList();  // 빈 리스트 반환
        }

        // 연별로 데이터를 그룹화하여 변환
        Map<Integer, List<LiveStock>> groupedByYear = dailyData.stream()
                .collect(Collectors.groupingBy(liveStock -> liveStock.getDate().getYear())); // 연도만 사용

        List<YearlyStockDTO> yearlyDataList = new ArrayList<>();

        // 연별로 데이터 처리 및 DTO 생성
        for (Map.Entry<Integer, List<LiveStock>> entry : groupedByYear.entrySet()) {
            String yearDateEntry = entry.getKey().toString();
            List<LiveStock> yearData = entry.getValue();

            // 연별 시가, 종가, 고가, 저가, 거래량 계산
            int yearlyStartPrice = yearData.get(0).getStartPrice();
            int yearlyEndPrice = yearData.get(yearData.size() - 1).getEndPrice();
            int yearlyHighPrice = yearData.stream().mapToInt(LiveStock::getHighPrice).max().orElse(0);
            int yearlyLowPrice = yearData.stream().mapToInt(LiveStock::getLowPrice).min().orElse(0);
            long yearlyVolume = yearData.stream().mapToLong(LiveStock::getVolume).sum();

            // 연도별 가장 작은 날짜 찾기 (연도별로 첫 번째 일자)
            String formattedDate = yearData.get(0).getDate().toString();  // 첫 번째 일자 (yyyy-mm-dd 형식)

            // YearlyStockDTO 객체 빌더 패턴으로 생성
            YearlyStockDTO yearlyStockDTO = YearlyStockDTO.builder()
                    .ticker(ticker)
                    .date(formattedDate)
                    .yearlyStartPrice(yearlyStartPrice)
                    .yearlyLowPrice(yearlyLowPrice)
                    .yearlyHighPrice(yearlyHighPrice)
                    .yearlyEndPrice(yearlyEndPrice)
                    .yearlyVolume(yearlyVolume)
                    .build();

            // 연별 데이터 리스트에 추가
            yearlyDataList.add(yearlyStockDTO);
        }

        yearlyDataList.sort(Comparator.comparing(yearlyStockDTO -> yearlyStockDTO.getDate()));

        return yearlyDataList;
    }

}
