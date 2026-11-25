package com.arise.corp.sga.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.sga.dto.ResumenSgaResponse;
import com.arise.corp.sga.service.ResumenSgaService;

/**
 * API de indicadores del SGA (SGA-06, Requirement 16).
 *
 * <p>Sin parametros: muestra el estado actual en una transaccion de solo
 * lectura.
 */
@RestController
@RequestMapping("/api/sga/resumen")
public class ResumenSgaController {

    private final ResumenSgaService resumenService;

    public ResumenSgaController(ResumenSgaService resumenService) {
        this.resumenService = resumenService;
    }

    @GetMapping
    public ResumenSgaResponse obtener() {
        return resumenService.obtener();
    }
}
