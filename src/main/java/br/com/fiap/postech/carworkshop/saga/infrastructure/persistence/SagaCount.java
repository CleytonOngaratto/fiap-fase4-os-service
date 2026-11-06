package br.com.fiap.postech.carworkshop.saga.infrastructure.persistence;

public record SagaCount(String key, String branchId, long count) {

    public static final String UNKNOWN_BRANCH = "unknown";

    public static SagaCount of(Object[] row) {
        String key = row[0] instanceof Enum<?> value ? value.name() : String.valueOf(row[0]);
        String branchId = row[1] == null || row[1].toString().isBlank() ? UNKNOWN_BRANCH : row[1].toString();
        return new SagaCount(key, branchId, ((Number) row[2]).longValue());
    }
}
