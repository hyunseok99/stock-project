package org.team4.sol_server.domain.stock.index;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockIndexService {

    @Autowired
    private StockIndexRepository stockIndexRepository;

    public List<StockIndexEntity> getAllIndices() {
        return stockIndexRepository.findAll();
    }
}

