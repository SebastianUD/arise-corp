package com.arise.corp.crm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arise.corp.crm.dto.ResumenComercialResponse;
import com.arise.corp.crm.service.ResumenComercialService;

/**
 * API del resumen comercial del CRM (CRM-05, Requirements 1.2 y 15).
 *
 * <p>La ruta queda bajo {@code /api/crm/**} en {@code SecurityConfig}, de modo
 * que solo los gestores del CRM acceden a los conteos.
 */
@RestController
@RequestMapping("/api/crm/resumen")
public class ResumenComercialController {

    private final ResumenComercialService resumenService;

    public ResumenComercialController(ResumenComercialService resumenService) {
        this.resumenService = resumenService;
    }

    @GetMapping
    public ResumenComercialResponse obtener() {
        return resumenService.obtener();
    }
}
