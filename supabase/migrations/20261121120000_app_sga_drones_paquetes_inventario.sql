-- Migracion: modulo SGA (drones, paquetes con historial de estados e inventario)
-- Spec: 004-sga-drones-paquetes-inventario
-- Decisiones: DP-ARQ-01, DP-ARQ-02, DP-SEG-05, DP-SEG-06, DP-TEC-03, DP-API-04
-- Requisitos: SGA-01 a SGA-05, NF-01, NF-03, NF-05
-- Solo estructura: no inserta datos, UUID personales ni secretos (NF-01).

-- ====================================
-- Drones (SGA-01)
-- ====================================
create table app.drones (
    id                      uuid        primary key default gen_random_uuid(),
    codigo                  text        not null,
    modelo                  text        not null,
    observaciones           text,
    estado                  text        not null default 'DISPONIBLE',
    estado_actualizado_en   timestamptz not null default now(),
    -- sub del JWT del actor; sin FK para conservar la auditoria (DP-SEG-06)
    estado_actualizado_por  uuid        not null,
    creado_en               timestamptz not null default now(),
    actualizado_en          timestamptz not null default now(),
    actualizado_por         uuid        not null,
    constraint drones_codigo_uk unique (codigo),
    constraint drones_codigo_ck check (codigo ~ '^[A-Z0-9-]{3,30}$'),
    constraint drones_modelo_ck check (char_length(modelo) between 1 and 100),
    constraint drones_observaciones_ck
        check (observaciones is null or char_length(observaciones) between 1 and 500),
    constraint drones_estado_ck
        check (estado in ('DISPONIBLE', 'EN_MISION', 'MANTENIMIENTO', 'FUERA_DE_SERVICIO'))
);

-- ====================================
-- Paquetes (SGA-02, SGA-05)
-- ====================================
create table app.paquetes (
    id                      uuid        primary key default gen_random_uuid(),
    codigo                  text        not null,
    descripcion             text        not null,
    direccion_entrega       text        not null,
    estado                  text        not null default 'REGISTRADO',
    -- Relaciones con el CRM por UUID; sin asociacion JPA entre modulos (DP-ARQ-01)
    cliente_id              uuid        not null references app.clientes (id),
    contrato_id             uuid        references app.contratos (id),
    dron_id                 uuid        references app.drones (id),
    estado_actualizado_en   timestamptz not null default now(),
    estado_actualizado_por  uuid        not null,
    creado_en               timestamptz not null default now(),
    actualizado_en          timestamptz not null default now(),
    actualizado_por         uuid        not null,
    constraint paquetes_codigo_uk unique (codigo),
    constraint paquetes_codigo_ck check (codigo ~ '^[A-Z0-9-]{3,30}$'),
    constraint paquetes_descripcion_ck check (char_length(descripcion) between 1 and 500),
    constraint paquetes_direccion_entrega_ck
        check (char_length(direccion_entrega) between 1 and 200),
    constraint paquetes_estado_ck
        check (estado in ('REGISTRADO', 'ASIGNADO', 'EN_TRANSITO', 'ENTREGADO', 'CANCELADO', 'DEVUELTO')),
    -- REGISTRADO sin dron; ASIGNADO, EN_TRANSITO, ENTREGADO y DEVUELTO con dron;
    -- CANCELADO con o sin dron segun se cancele antes o despues de asignarlo (SGA-05)
    constraint paquetes_dron_estado_ck check (
        (estado = 'REGISTRADO' and dron_id is null)
        or (estado in ('ASIGNADO', 'EN_TRANSITO', 'ENTREGADO', 'DEVUELTO') and dron_id is not null)
        or estado = 'CANCELADO')
);

-- Un dron tiene como maximo un paquete en curso (SGA-05, NF-03)
create unique index paquetes_dron_en_curso_uk on app.paquetes (dron_id)
    where estado in ('ASIGNADO', 'EN_TRANSITO');
create index paquetes_cliente_id_idx on app.paquetes (cliente_id);
create index paquetes_contrato_id_idx on app.paquetes (contrato_id);
create index paquetes_dron_id_idx on app.paquetes (dron_id);

-- ====================================
-- Historial de estados del paquete (SGA-04)
-- ====================================
create table app.historial_estados_paquete (
    id               uuid        primary key default gen_random_uuid(),
    paquete_id       uuid        not null references app.paquetes (id),
    -- Orden del registro dentro del paquete, independiente del reloj
    secuencia        smallint    not null,
    estado_anterior  text,
    estado_nuevo     text        not null,
    dron_id          uuid        references app.drones (id),
    observacion      text,
    registrado_en    timestamptz not null default now(),
    registrado_por   uuid        not null,
    constraint historial_estados_paquete_secuencia_uk unique (paquete_id, secuencia),
    constraint historial_estados_paquete_secuencia_ck check (secuencia between 1 and 4),
    constraint historial_estados_paquete_estado_anterior_ck
        check (estado_anterior is null or estado_anterior in
               ('REGISTRADO', 'ASIGNADO', 'EN_TRANSITO', 'ENTREGADO', 'CANCELADO', 'DEVUELTO')),
    constraint historial_estados_paquete_estado_nuevo_ck
        check (estado_nuevo in ('REGISTRADO', 'ASIGNADO', 'EN_TRANSITO', 'ENTREGADO', 'CANCELADO', 'DEVUELTO')),
    -- El alta es el primer registro: sin estado anterior y en REGISTRADO
    constraint historial_estados_paquete_alta_ck check (
        ((secuencia = 1) = (estado_anterior is null))
        and (secuencia > 1 or estado_nuevo = 'REGISTRADO')),
    -- El dron se registra solo en la transicion a ASIGNADO
    constraint historial_estados_paquete_dron_ck
        check ((estado_nuevo = 'ASIGNADO') = (dron_id is not null)),
    constraint historial_estados_paquete_observacion_ck
        check (observacion is null or char_length(observacion) between 1 and 500)
);

-- ====================================
-- Articulos de inventario (SGA-03)
-- ====================================
create table app.articulos_inventario (
    id               uuid        primary key default gen_random_uuid(),
    codigo           text        not null,
    nombre           text        not null,
    categoria        text        not null,
    unidad           text        not null,
    stock_minimo     integer     not null,
    -- Solo cambia con movimientos (SGA-03); bigint para que las entradas no desborden
    existencia       bigint      not null default 0,
    creado_en        timestamptz not null default now(),
    actualizado_en   timestamptz not null default now(),
    actualizado_por  uuid        not null,
    constraint articulos_inventario_codigo_uk unique (codigo),
    constraint articulos_inventario_codigo_ck check (codigo ~ '^[A-Z0-9-]{3,30}$'),
    constraint articulos_inventario_nombre_ck check (char_length(nombre) between 1 and 200),
    constraint articulos_inventario_categoria_ck
        check (categoria in ('REPUESTO', 'BATERIA', 'EMPAQUE', 'OTRO')),
    constraint articulos_inventario_unidad_ck
        check (unidad in ('UNIDAD', 'CAJA', 'JUEGO', 'ROLLO')),
    constraint articulos_inventario_stock_minimo_ck check (stock_minimo between 0 and 1000000),
    constraint articulos_inventario_existencia_ck check (existencia >= 0)
);

-- ====================================
-- Movimientos de inventario (SGA-03)
-- ====================================
create table app.movimientos_inventario (
    id                     uuid        primary key default gen_random_uuid(),
    articulo_id            uuid        not null references app.articulos_inventario (id),
    tipo                   text        not null,
    cantidad               integer     not null,
    existencia_anterior    bigint      not null,
    existencia_resultante  bigint      not null,
    observacion            text,
    registrado_en          timestamptz not null default now(),
    registrado_por         uuid        not null,
    constraint movimientos_inventario_tipo_ck check (tipo in ('ENTRADA', 'SALIDA', 'AJUSTE')),
    constraint movimientos_inventario_cantidad_ck check (
        (tipo in ('ENTRADA', 'SALIDA') and cantidad between 1 and 1000000)
        or (tipo = 'AJUSTE' and cantidad between 0 and 1000000)),
    constraint movimientos_inventario_existencia_ck
        check (existencia_anterior >= 0 and existencia_resultante >= 0),
    constraint movimientos_inventario_aritmetica_ck check (
        (tipo = 'ENTRADA' and existencia_resultante = existencia_anterior + cantidad)
        or (tipo = 'SALIDA' and existencia_resultante = existencia_anterior - cantidad)
        or (tipo = 'AJUSTE' and existencia_resultante = cantidad)),
    constraint movimientos_inventario_observacion_ck
        check (observacion is null or char_length(observacion) between 1 and 500),
    constraint movimientos_inventario_observacion_ajuste_ck
        check (tipo <> 'AJUSTE' or observacion is not null)
);

create index movimientos_inventario_articulo_registrado_idx
    on app.movimientos_inventario (articulo_id, registrado_en);

-- ====================================
-- Comentarios (resumen)
-- ====================================
comment on table app.drones is 'Drones de entrega; FUERA_DE_SERVICIO hace las veces de desactivacion (SGA-01).';
comment on table app.paquetes is 'Paquetes asociados a cliente, contrato opcional y dron; estados controlados (SGA-02, SGA-05).';
comment on table app.historial_estados_paquete is 'Historial inmutable de estados del paquete (SGA-04).';
comment on table app.articulos_inventario is 'Catalogo de inventario; la existencia solo cambia con movimientos (SGA-03).';
comment on table app.movimientos_inventario is 'Entradas, salidas y ajustes de inventario; no se editan (SGA-03).';

-- ====================================
-- Seguridad: RLS y revocacion de grants (DP-SEG-05)
-- ====================================
alter table app.drones enable row level security;
alter table app.paquetes enable row level security;
alter table app.historial_estados_paquete enable row level security;
alter table app.articulos_inventario enable row level security;
alter table app.movimientos_inventario enable row level security;

-- La revocacion explicita no depende de los privilegios por defecto del esquema.
revoke all on table app.drones, app.paquetes, app.historial_estados_paquete,
    app.articulos_inventario, app.movimientos_inventario
    from anon, authenticated;
