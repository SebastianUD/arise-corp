package com.arise.corp.erp.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.erp.dto.ResumenFinancieroResponse;
import com.arise.corp.erp.service.RangoFechas;
import com.arise.corp.erp.service.ResumenFinancieroService;

/**
 * API del panel financiero del ERP (ERP-04, Requirement 18).
 *
 * <p>Los limites son opcionales y se validan al construir {@link RangoFechas};
 * una fecha invalida llega como error de conversion de Spring MVC y
 * {@code desde > hasta} como {@code ParametroInvalidoException} sobre
 * {@code hasta}.
 */
@RestController
@RequestMapping("/api/erp/resumen")
public class ResumenFinancieroController {

    private final ResumenFinancieroService resumenService;

    public ResumenFinancieroController(ResumenFinancieroService resumenService) {
        this.resumenService = resumenService;
    }

    @GetMapping
    public ResumenFinancieroResponse obtener(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return resumenService.obtener(new RangoFechas(desde, hasta));
    }
}
