package dev.abstratium.abstrauth.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts between {@link DefaultAssignment} enum values and their lowercase
 * string representation in the database column.
 *
 * <p>This is used instead of {@link jakarta.persistence.EnumType#STRING}
 * (which persists the Java enum constant name in uppercase) so that the
 * database stores the lowercase {@link DefaultAssignment#getDbValue() db value}
 * that matches the CHECK constraint and the Flyway migration backfill.</p>
 */
@Converter(autoApply = false)
public class DefaultAssignmentConverter implements AttributeConverter<DefaultAssignment, String> {

    @Override
    public String convertToDatabaseColumn(DefaultAssignment attribute) {
        if (attribute == null) {
            return DefaultAssignment.NOT_DEFAULT.getDbValue();
        }
        return attribute.getDbValue();
    }

    @Override
    public DefaultAssignment convertToEntityAttribute(String dbData) {
        return DefaultAssignment.fromDbValue(dbData);
    }
}
