package com.arise.corp.shared.paginacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.arise.corp.crm.GeneradoresCrm;

/**
 * Pruebas de la construccion de {@link Pageable} y de la respuesta paginada
 * (DP-API-03, D7, Property 14 en su parte unitaria).
 */
class PaginacionTests {

    private static final int CASOS = 120;

    private static final Set<String> CAMPOS = Set.of(
            "id", "nombre", "numeroDocumento", "creadoEn");

    @Test
    void elOrdenPorDefectoEsIdAscendente() {
        Pageable pagina = Paginacion.crear(0, 20, null, CAMPOS);

        assertEquals(0, pagina.getPageNumber());
        assertEquals(20, pagina.getPageSize());

        List<Sort.Order> ordenes = pagina.getSort().toList();
        assertEquals("id", ordenes.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, ordenes.get(0).getDirection());
    }

    @Test
    void elOrdenVacioEquivaleAlOrdenPorDefecto() {
        for (String vacio : new String[] {"", "   ", "\t"}) {
            Pageable pagina = Paginacion.crear(1, 5, vacio, CAMPOS);

            assertEquals("id",
                    pagina.getSort().toList().get(0).getProperty(),
                    "orden por defecto incorrecto para: <" + vacio + ">");
        }
    }

    @Test
    void conservaElCampoSolicitadoYAgregaElDesempatePorId() {
        Pageable pagina = Paginacion.crear(2, 10, "nombre,desc", CAMPOS);

        List<Sort.Order> ordenes = pagina.getSort().toList();
        assertEquals("nombre", ordenes.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, ordenes.get(0).getDirection());
        assertEquals("id", ordenes.get(ordenes.size() - 1).getProperty());
        assertEquals(Sort.Direction.ASC,
                ordenes.get(ordenes.size() - 1).getDirection());
    }

    @Test
    void aceptaLaDireccionSinImportarMayusculasYRecortaElCampo() {
        Pageable pagina = Paginacion.crear(0, 10, "  nombre , DESC ", CAMPOS);

        Sort.Order principal = pagina.getSort().toList().get(0);
        assertEquals("nombre", principal.getProperty());
        assertEquals(Sort.Direction.DESC, principal.getDirection());
    }

    @Test
    void rechazaCamposFueraDeLaListaPermitida() {
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 10, "correo,asc", CAMPOS));
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 10, "NOMBRE,asc", CAMPOS));
    }

    @Test
    void rechazaDireccionesYFormatosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 10, "nombre,up", CAMPOS));
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 10, "nombre,", CAMPOS));
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 10, "nombre", CAMPOS));
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 10, "nombre,asc,id", CAMPOS));
    }

    @Test
    void rechazaPaginasYtamanosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(-1, 10, null, CAMPOS));
        assertThrows(IllegalArgumentException.class,
                () -> Paginacion.crear(0, 0, null, CAMPOS));
    }

    @Test
    void laPaginaResponseConservaLosMetadatos() {
        PageImpl<String> pagina = new PageImpl<>(
                List.of("a", "b"),
                PageRequest.of(1, 2),
                5);

        PaginaResponse<String> respuesta = PaginaResponse.desde(pagina);

        assertEquals(List.of("a", "b"), respuesta.content());
        assertEquals(1, respuesta.page());
        assertEquals(2, respuesta.size());
        assertEquals(5, respuesta.totalElements());
        assertEquals(3, respuesta.totalPages());
    }

    @Test
    @DisplayName("Feature: 002-crm-clientes-contratos, "
            + "Property 14: Los parámetros de lista se aceptan o rechazan según DP-API-03")
    void elOrdenSeAceptaSoloSiUsaUnCampoPermitidoConDireccionValida() {
        Random azar = GeneradoresCrm.nuevoRandom();
        List<String> campos = List.of(CAMPOS.toArray(String[]::new));
        List<String> ajenos = List.of("correo", "clave", "fechaFin", "razonSocial");
        List<String> direcciones = List.of("asc", "desc", "ASC", "DESC", "up", "");

        for (int i = 0; i < CASOS; i++) {
            boolean permitido = azar.nextBoolean();
            String campo = permitido
                    ? campos.get(azar.nextInt(campos.size()))
                    : ajenos.get(azar.nextInt(ajenos.size()));
            String direccion = direcciones.get(azar.nextInt(direcciones.size()));
            String sort = campo + "," + direccion;

            boolean direccionValida = direccion.equalsIgnoreCase("asc")
                    || direccion.equalsIgnoreCase("desc");
            boolean esperado = permitido && direccionValida;
            String contexto = "sort=<" + sort + ">";

            if (esperado) {
                Pageable pagina = Paginacion.crear(0, 10, sort, CAMPOS);
                Sort.Order principal = pagina.getSort().toList().get(0);

                assertEquals(campo, principal.getProperty(), contexto);
                assertEquals(Sort.Direction.fromString(
                                direccion.toUpperCase(java.util.Locale.ROOT)),
                        principal.getDirection(),
                        contexto);
                assertEquals("id", pagina.getSort().toList()
                        .get(pagina.getSort().toList().size() - 1).getProperty(),
                        contexto);
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> Paginacion.crear(0, 10, sort, CAMPOS),
                        contexto);
            }
        }
    }

    @Test
    void laPaginaResponseEsInmutable() {
        PaginaResponse<String> respuesta = PaginaResponse.desde(
                new PageImpl<>(List.of("a"), PageRequest.of(0, 1), 1));

        assertThrows(UnsupportedOperationException.class,
                () -> respuesta.content().add("b"));
        assertTrue(respuesta.totalPages() >= 0);
    }
}
