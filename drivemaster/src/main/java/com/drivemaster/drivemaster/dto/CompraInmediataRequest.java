package com.drivemaster.drivemaster.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompraInmediataRequest {
    private List<CartItemRequest> productos;
    private String metodoPago;
}
