package org.team4.sol_server.domain.stock.index;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/stock")
public class StockIndexController {

    @Autowired
    private StockIndexService  stockIndexService;

    @GetMapping("/indices")
    public ResponseEntity<List<StockIndexEntity>> getAllIndices() {
        List<StockIndexEntity> indices = stockIndexService.getAllIndices();
        return ResponseEntity.ok(indices);
    }
}
