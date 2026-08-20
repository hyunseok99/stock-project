package org.team4.sol_server.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

@Converter(autoApply = true)
public class LocalDateConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate localDate) {
        // case: return "yyyy-MM-dd"
        return (localDate == null) ? null : localDate.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbText) {
        return (dbText == null || dbText.isEmpty()) ? null : LocalDate.parse(dbText) ;
    }

}
