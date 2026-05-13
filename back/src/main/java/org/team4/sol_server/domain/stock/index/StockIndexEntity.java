package org.team4.sol_server.domain.stock.index;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "stock_index")
@Data
public class StockIndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_name")
    private String indexName;

    @Column(name = "current")
    private BigDecimal current;

    @Column(name = "change_value")
    private BigDecimal changeValue;

    @Column(name = "change_percent")
    private BigDecimal changePercent;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    @Column(name = "date")
    private LocalDateTime date;

}
