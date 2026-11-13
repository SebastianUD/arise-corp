package com.arise.corp.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.arise.corp.crm.GeneradoresCrm;
import com.arise.corp.crm.dto.ResumenComercialResponse;
import com.arise.corp.crm.entity.EstadoContrato;
import com.arise.corp.crm.repository.ClienteRepository;
import com.arise.corp.crm.repository.ConteoPorClave;
import com.arise.corp.crm.repository.ContratoRepository;

/**
 * Pruebas del resumen comercial (CRM-05, Requirements 15.1 a 15.3).
 *
 * <p>Mockito simula las dos consultas agrupadas y Property 13 recorre conteos
 * generados con semilla fija para comprobar que los totales siempre son la
 * suma de sus componentes.
 */
class ResumenComercialServiceTests {

    private static final int CASOS = 120;

    private ClienteRepository clientes;
    private ContratoRepository contratos;
    private ResumenComercialService servicio;

    @BeforeEach
    void setUp() {
        clientes = mock(ClienteRepository.class);
        contratos = mock(ContratoRepository.class);
        servicio = new ResumenComercialService(clientes, contratos);
    }

    @Test
    void devuelveLosCuatroEstadosEnCeroCuandoNoHayDatos() {
        when(clientes.contarPorActivo()).thenReturn(List.of());
        when(contratos.contarPorEstado()).thenReturn(List.of());

        ResumenComercialResponse resumen = servicio.obtener();

        assertEquals(0L, resumen.clientes().activos());
        assertEquals(0L, resumen.clientes().inactivos());
        assertEquals(0L, resumen.clientes().total());
        assertEquals(0L, resumen.contratos().total());
        assertEquals(EstadoContrato.values().length, resumen.contratos().porEstado().size());

        for (EstadoContrato estado : EstadoContrato.values()) {
            assertEquals(0L, resumen.contratos().porEstado().get(estado),
                    "estado sin contratos distinto de cero: " + estado);
        }
    }

    @Test
    void cuentaLosClientesActivosEInactivosAunqueFalteUnGrupo() {
        when(clientes.contarPorActivo())
                .thenReturn(List.of(new Conteo<>(Boolean.TRUE, 7L)));
        when(contratos.contarPorEstado()).thenReturn(List.of());

        ResumenComercialResponse resumen = servicio.obtener();

        assertEquals(7L, resumen.clientes().activos());
        assertEquals(0L, resumen.clientes().inactivos());
        assertEquals(7L, resumen.clientes().total());
    }

    @Test
    void ejecutaSoloLasDosConsultasAgrupadas() {
        when(clientes.contarPorActivo()).thenReturn(List.of());
        when(contratos.contarPorEstado()).thenReturn(List.of());

        servicio.obtener();

        verify(clientes).contarPorActivo();
        verify(contratos).contarPorEstado();
        verifyNoMoreInteractions(clientes, contratos);
    }

    @Test
    void seEjecutaEnUnaTransaccionDeSoloLectura() {
        Transactional transaccion = ResumenComercialService.class
                .getAnnotation(Transactional.class);

        assertNotNull(transaccion, "el servicio no declara @Transactional");
        assertTrue(transaccion.readOnly(), "la transaccion no es de solo lectura");
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 13: El resumen comercial es consistente")
    void elResumenEsConsistenteConLosConteosAgrupados() {
        Random azar = GeneradoresCrm.nuevoRandom();

        for (int i = 0; i < CASOS; i++) {
            long activos = azar.nextLong(500);
            long inactivos = azar.nextLong(500);
            List<ConteoPorClave<Boolean>> porActivo = new ArrayList<>();
            long esperadoActivos = 0L;
            long esperadoInactivos = 0L;
            if (azar.nextBoolean()) {
                porActivo.add(new Conteo<>(Boolean.TRUE, activos));
                esperadoActivos = activos;
            }
            if (azar.nextBoolean()) {
                porActivo.add(new Conteo<>(Boolean.FALSE, inactivos));
                esperadoInactivos = inactivos;
            }
            if (azar.nextInt(4) == 0) {
                porActivo.clear();
                esperadoActivos = 0L;
                esperadoInactivos = 0L;
            }

            Map<EstadoContrato, Long> esperado = new EnumMap<>(EstadoContrato.class);
            List<ConteoPorClave<EstadoContrato>> porEstado = new ArrayList<>();
            for (EstadoContrato estado : EstadoContrato.values()) {
                if (azar.nextBoolean()) {
                    long total = azar.nextLong(500);
                    esperado.put(estado, total);
                    porEstado.add(new Conteo<>(estado, total));
                }
            }

            when(clientes.contarPorActivo()).thenReturn(porActivo);
            when(contratos.contarPorEstado()).thenReturn(porEstado);

            ResumenComercialResponse resumen = servicio.obtener();
            String contexto = "caso " + i + " con activos=" + esperadoActivos
                    + " inactivos=" + esperadoInactivos + " estados=" + esperado;

            assertEquals(esperadoActivos, resumen.clientes().activos(), contexto);
            assertEquals(esperadoInactivos, resumen.clientes().inactivos(), contexto);
            assertEquals(esperadoActivos + esperadoInactivos,
                    resumen.clientes().total(), contexto);

            Map<EstadoContrato, Long> obtenido = resumen.contratos().porEstado();
            assertInstanceOf(EnumMap.class, obtenido, contexto);
            assertEquals(EstadoContrato.values().length, obtenido.size(), contexto);

            long totalContratos = 0L;
            for (EstadoContrato estado : EstadoContrato.values()) {
                long cantidad = esperado.getOrDefault(estado, 0L);
                assertEquals(cantidad, obtenido.get(estado),
                        contexto + " y estado " + estado);
                totalContratos += cantidad;
            }
            assertEquals(totalContratos, resumen.contratos().total(), contexto);
        }
    }

    private record Conteo<K>(K clave, long total) implements ConteoPorClave<K> {

        @Override
        public K getClave() {
            return clave;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }
}
