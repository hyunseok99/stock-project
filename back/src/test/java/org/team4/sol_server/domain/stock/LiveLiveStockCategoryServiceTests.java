//package org.team4.sol_server.domain.stock;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.team4.sol_server.domain.stock.dto.DailyStockDTO;
//import org.team4.sol_server.domain.stock.dto.MonthlyStockDTO;
//import org.team4.sol_server.domain.stock.dto.WeeklyStockDTO;
//import org.team4.sol_server.domain.stock.dto.YearlyStockDTO;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//@SpringBootTest
//public class LiveLiveStockCategoryServiceTests {
//    @Mock
//    private LiveStockRepository liveStockRepository;  // Mock StockRepository
//
//    @InjectMocks
//    private LiveStockService liveStockService;  // StockService에 Mock된 repository를 주입
//
//    private String ticker;
//    private String endDate;
//    private String oldDate;
//    @BeforeEach
//    void setUp() {
//        ticker = "005930";  // Test ticker symbol
//        endDate = "2025-03-10";  // Test date
//        oldDate = "1800-01-01"; // Test date
//    }
//
//    @Test
//    void testGetDailyStock() {
//        // 정상적으로 db에 데이터가 존재하는 경우
//        List<DailyStockDTO> result = liveStockService.getDailyStockData(ticker, endDate);
//
//        // 요청 날짜 이전에 데이터가 없는 경우
//        List<DailyStockDTO> result2 = liveStockService.getDailyStockData(ticker, oldDate);
//
//        // 정상적인 경우 널이 아님
//        assertNotNull(result);
//
//        // 이전 데이터 없는 경우 빈 리스트
//        assertTrue(result2.isEmpty());
//
//    }
//
//    @Test
//    void testGetWeeklyStock() {
//        // 정상적으로 db에 데이터가 존재하는 경우
//        List<WeeklyStockDTO> result = liveStockService.getWeeklyStockData(ticker, endDate);
//
//        // 요청 날짜 이전에 데이터가 없는 경우
//        List<WeeklyStockDTO> result2 = liveStockService.getWeeklyStockData(ticker, oldDate);
//
//        // 정상적인 경우 널이 아님
//        assertNotNull(result);
//
//        // 이전 데이터 없는 경우 빈 리스트
//        assertTrue(result2.isEmpty());
//
//    }
//
//    @Test
//    void testGetMonthlyStock() {
//        // 정상적으로 db에 데이터가 존재하는 경우
//        List<MonthlyStockDTO> result = liveStockService.getMonthlyStockData(ticker, endDate);
//
//        // 요청 날짜 이전에 데이터가 없는 경우
//        List<MonthlyStockDTO> result2 = liveStockService.getMonthlyStockData(ticker, oldDate);
//
//        // 정상적인 경우 널이 아님
//        assertNotNull(result);
//
//        // 이전 데이터 없는 경우 빈 리스트
//        assertTrue(result2.isEmpty());
//
//    }
//
//    @Test
//    void testGetYearlyStock() {
//        // 정상적으로 db에 데이터가 존재하는 경우
//        List<YearlyStockDTO> result = liveStockService.getYearlyStockData(ticker, endDate);
//
//        // 요청 날짜 이전에 데이터가 없는 경우
//        List<YearlyStockDTO> result2 = liveStockService.getYearlyStockData(ticker, oldDate);
//
//        // 정상적인 경우 널이 아님
//        assertNotNull(result);
//
//        // 이전 데이터 없는 경우 빈 리스트
//        assertTrue(result2.isEmpty());
//
//    }
//}
