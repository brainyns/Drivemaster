package com.drivemaster.drivemaster.exception;

public class StockInsuficienteException extends RuntimeException {
    private final String producto;
    private final Integer stockDisponible;

    public StockInsuficienteException(String producto, Integer stockDisponible) {
        super("Stock insuficiente para \"" + producto + "\". Disponible: " + stockDisponible);
        this.producto = producto;
        this.stockDisponible = stockDisponible;
    }

    public String getProducto() {
        return producto;
    }

    public Integer getStockDisponible() {
        return stockDisponible;
    }
}
