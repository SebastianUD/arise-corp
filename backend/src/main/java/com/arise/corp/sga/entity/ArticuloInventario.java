package com.arise.corp.sga.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Articulo del inventario del SGA (SGA-03).
 *
 * <p>La existencia solo cambia con {@link #aplicarMovimiento}: los datos se
 * editan con {@link #actualizarDatos}, que no la recibe (Requirement 15.12).
 * Un movimiento no toca la auditoria de datos (D14). {@code existencia} es
 * {@code long} para que una suma de entradas no desborde (D5).
 */
@Entity
@Table(
        name = "articulos_inventario",
        uniqueConstraints = @UniqueConstraint(
                name = "articulos_inventario_codigo_uk",
                columnNames = "codigo"))
public class ArticuloInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "codigo", nullable = false)
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false)
    private CategoriaArticulo categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad", nullable = false)
    private UnidadArticulo unidad;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    @Column(name = "existencia", nullable = false)
    private long existencia;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por", nullable = false)
    private UUID actualizadoPor;

    protected ArticuloInventario() {
        // Requerido por JPA.
    }

    private ArticuloInventario(
            String codigo,
            String nombre,
            CategoriaArticulo categoria,
            UnidadArticulo unidad,
            int stockMinimo,
            long existencia,
            Instant creadoEn,
            Instant actualizadoEn,
            UUID actualizadoPor) {

        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
        this.stockMinimo = stockMinimo;
        this.existencia = existencia;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
        this.actualizadoPor = actualizadoPor;
    }

    /**
     * Crea un articulo con existencia 0 y la auditoria del actor
     * (Requirement 13.1).
     */
    public static ArticuloInventario crear(
            String codigo,
            String nombre,
            CategoriaArticulo categoria,
            UnidadArticulo unidad,
            int stockMinimo,
            UUID actor,
            Instant ahora) {

        return new ArticuloInventario(
                codigo,
                nombre,
                categoria,
                unidad,
                stockMinimo,
                0L,
                ahora,
                ahora,
                actor);
    }

    /**
     * Reemplaza los datos y la auditoria sin recibir la existencia
     * (Requirement 13.8).
     */
    public void actualizarDatos(
            String codigo,
            String nombre,
            CategoriaArticulo categoria,
            UnidadArticulo unidad,
            int stockMinimo,
            UUID actor,
            Instant ahora) {

        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
        this.stockMinimo = stockMinimo;
        this.actualizadoEn = ahora;
        this.actualizadoPor = actor;
    }

    /**
     * Aplica la aritmetica del tipo y fija la existencia resultante sin tocar
     * la auditoria de datos (D12, D14).
     *
     * @return la existencia resultante
     */
    public long aplicarMovimiento(TipoMovimientoInventario tipo, int cantidad) {
        long resultante = tipo.existenciaResultante(existencia, cantidad);

        if (resultante < 0) {
            throw new IllegalStateException(
                    "La existencia no puede quedar negativa: " + resultante + ".");
        }

        this.existencia = resultante;
        return resultante;
    }

    /**
     * {@code true} si la existencia es estrictamente menor que el stock minimo
     * (Requirement 14.6); un stock minimo 0 nunca cuenta.
     */
    public boolean bajoStockMinimo() {
        return existencia < stockMinimo;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaArticulo getCategoria() {
        return categoria;
    }

    public UnidadArticulo getUnidad() {
        return unidad;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public long getExistencia() {
        return existencia;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public UUID getActualizadoPor() {
        return actualizadoPor;
    }
}
