-- Datos sinteticos para la presentacion. Ejecutar mediante DatosPresentacion.java.
-- No son relaciones comerciales, contactos ni identificaciones de personas reales.
-- El ejecutor realiza copia, borrado ordenado y COMMIT o ROLLBACK del conjunto.
DO $$
DECLARE
  actor uuid := current_setting('arise.actor')::uuid;
  base timestamptz := date_trunc('day', now() AT TIME ZONE 'America/Bogota') AT TIME ZONE 'America/Bogota';
  nombres text[] := ARRAY['Andina Medical S.A.S.', 'EcoMarket Colombia', 'Laura Méndez', 'FarmaExpress S.A.S.',
    'TechBox S.A.S.', 'Clínica Horizonte', 'Camilo Restrepo', 'Laboratorios Sierra', 'Mercado Raíz',
    'Valentina Rojas', 'Óptica Prisma', 'Repuestos Altura', 'Librería Arrayán', 'Daniel Cárdenas',
    'Café Monteluz', 'Floristería Magnolia', 'Natalia Pineda', 'BioSalud Integral', 'Tienda Nómada',
    'Mateo Salazar', 'Panadería La Espiga', 'Textiles Aurora', 'Isabela Torres', 'Veterinaria Huellas',
    'Cosmética Botánica', 'Santiago Moreno', 'Papelería Punto Azul'];
  grupos text[] := ARRAY['Grupo Andina', 'Comercio Verde', 'Red Salud Horizonte', 'Alianza Urbana'];
  personas text[] := ARRAY['Mariana López', 'Andrés Beltrán', 'Sofía Ramírez', 'Felipe Acosta',
    'Catalina Vargas', 'Diego Rincón', 'Juliana Castro', 'Nicolás Herrera'];
  cargos text[] := ARRAY['Coordinadora de operaciones', 'Piloto de drones', 'Ejecutiva comercial',
    'Analista financiero', 'Ingeniera de mantenimiento', 'Auxiliar de inventario', 'Líder de talento humano', 'Desarrollador de software'];
  areas text[] := ARRAY['Operaciones', 'Operaciones', 'Comercial', 'Finanzas', 'Operaciones', 'Logística', 'Talento humano', 'Tecnología'];
  proveedores text[] := ARRAY['AeroPartes Colombia', 'Energía Andina', 'Empaques Circulares',
    'Taller Aeronáutico Central', 'Conectividad Urbana', 'Seguridad Altavista'];
  articulos text[] := ARRAY['Batería de vuelo de alta capacidad', 'Hélices de repuesto', 'Caja térmica para medicamentos',
    'Precintos de seguridad', 'Kit de mantenimiento preventivo', 'Cinta de embalaje', 'Sensor de proximidad', 'Espuma protectora'];
  categorias text[] := ARRAY['BATERIA','REPUESTO','EMPAQUE','EMPAQUE','REPUESTO','EMPAQUE','REPUESTO','OTRO'];
  unidades text[] := ARRAY['UNIDAD','JUEGO','UNIDAD','CAJA','JUEGO','ROLLO','UNIDAD','CAJA'];
  existencias integer[] := ARRAY[6,18,24,3,8,30,2,12];
  minimos integer[] := ARRAY[8,10,12,5,4,10,4,6];
  estados text[] := ARRAY['REGISTRADO','ASIGNADO','EN_TRANSITO','ENTREGADO','CANCELADO','DEVUELTO'];
  descripciones text[] := ARRAY['Medicamentos de entrega prioritaria', 'Compra de productos orgánicos',
    'Documentos para entrega personal', 'Insumos farmacéuticos', 'Accesorios electrónicos', 'Muestras de laboratorio'];
  i integer; j integer; n integer; persona boolean; cliente uuid; contrato uuid; dron uuid; paquete uuid; articulo uuid;
  estado text; anterior text; ruta text[]; instante timestamptz;
BEGIN
  FOR i IN 1..4 LOOP
    INSERT INTO app.cuentas_corporativas(id,nit,razon_social,nombre_contacto,correo_contacto,telefono_contacto,actualizado_por)
    VALUES(md5('arise-cuenta-'||i)::uuid, '90080000'||i, grupos[i], personas[i],
      'contacto'||i||'@example.com','601555010'||i,actor);
  END LOOP;

  FOR i IN 1..27 LOOP
    persona := i IN (3,7,10,14,17,20,23,26);
    INSERT INTO app.clientes(id,tipo_documento,numero_documento,nombre,correo,telefono,direccion,ciudad,cuenta_corporativa_id,activo,creado_en,actualizado_en,actualizado_por)
    VALUES(md5('arise-cliente-'||i)::uuid, CASE WHEN persona THEN 'CC' ELSE 'NIT' END,
      CASE WHEN persona THEN (1024000000+i)::text ELSE (901200000+i)::text END, nombres[i],
      'contacto'||i||'@example.com','300555'||lpad(i::text,4,'0'),
      'Carrera '||(10+i)||' # '||(40+i)||'-20',
      (ARRAY['Bogotá','Medellín','Cali','Bucaramanga','Barranquilla'])[(i-1)%5+1],
      CASE WHEN persona OR i=5 THEN NULL ELSE md5('arise-cuenta-'||((i-1)%4+1))::uuid END,
      i NOT IN (5,13,22),base-make_interval(days=>30-i),base-make_interval(days=>30-i),actor);
  END LOOP;

  FOR i IN 1..10 LOOP
    INSERT INTO app.contratos(id,cliente_id,codigo,descripcion,fecha_inicio,fecha_fin,valor,estado,
      estado_actualizado_en,estado_actualizado_por,creado_en,actualizado_en,actualizado_por)
    VALUES(md5('arise-contrato-'||i)::uuid,md5('arise-cliente-'||i)::uuid,
      'CTR-2026-'||lpad(i::text,3,'0'),'Servicio de distribución de última milla para '||nombres[i],
      (base::date-60),(base::date+305),1500000+i*450000,
      CASE WHEN i<=6 THEN 'ACTIVO' WHEN i<=8 THEN 'BORRADOR' WHEN i=9 THEN 'FINALIZADO' ELSE 'CANCELADO' END,
      base-interval '10 days',actor,base-interval '20 days',base-interval '10 days',actor);
  END LOOP;

  FOR i IN 1..8 LOOP
    INSERT INTO app.empleados(id,tipo_documento,numero_documento,nombre,cargo,area,correo,telefono,fecha_ingreso,activo,actualizado_por)
    VALUES(md5('arise-empleado-'||i)::uuid,'CC',(1035000000+i)::text,personas[i],cargos[i],areas[i],
      'equipo'||i||'@example.com','310555020'||i,base::date-(i*30),i<>8,actor);
  END LOOP;
  FOR i IN 1..6 LOOP
    INSERT INTO app.proveedores(id,tipo_documento,numero_documento,nombre,nombre_contacto,correo,telefono,direccion,ciudad,activo,actualizado_por)
    VALUES(md5('arise-proveedor-'||i)::uuid,'NIT',(900600000+i)::text,proveedores[i],personas[i],
      'compras'||i||'@example.com','601555030'||i,'Calle '||(60+i)||' # 20-30','Bogotá',i<>6,actor);
  END LOOP;
  FOR i IN 1..16 LOOP
    INSERT INTO app.movimientos_financieros(id,tipo,fecha,concepto,valor,referencia,estado,cliente_id,proveedor_id,
      estado_actualizado_en,estado_actualizado_por,actualizado_por)
    VALUES(md5('arise-finanza-'||i)::uuid,CASE WHEN i<=10 THEN 'INGRESO' ELSE 'EGRESO' END,
      base::date-(i-1),CASE WHEN i<=10 THEN 'Servicio de entregas - '||nombres[i]
      ELSE 'Abastecimiento y soporte - '||proveedores[i-10] END,
      CASE WHEN i<=10 THEN 280000+i*75000 ELSE 180000+(i-10)*45000 END,
      'FAC-2026-'||lpad(i::text,4,'0'),CASE WHEN i%5=0 THEN 'ANULADO' WHEN i%3=0 THEN 'PENDIENTE' ELSE 'PAGADO' END,
      CASE WHEN i<=10 THEN md5('arise-cliente-'||i)::uuid END,
      CASE WHEN i>10 THEN md5('arise-proveedor-'||(i-10))::uuid END,base-make_interval(days=>i-1),actor,actor);
  END LOOP;

  FOR i IN 1..8 LOOP
    INSERT INTO app.drones(id,codigo,modelo,observaciones,estado,estado_actualizado_por,actualizado_por)
    VALUES(md5('arise-dron-'||i)::uuid,'DRN-BOG-'||lpad(i::text,3,'0'),
      CASE WHEN i%2=0 THEN 'Arise Cargo M2' ELSE 'Arise Courier X1' END,
      CASE WHEN i=7 THEN 'Revisión preventiva del sistema de propulsión.' WHEN i=8 THEN 'Retirado para renovación de la flota.' ELSE 'Operación de última milla en la zona urbana de Bogotá.' END,
      CASE WHEN i IN (3,4) THEN 'EN_MISION' WHEN i=7 THEN 'MANTENIMIENTO' WHEN i=8 THEN 'FUERA_DE_SERVICIO' ELSE 'DISPONIBLE' END,
      actor,actor);
  END LOOP;

  FOR i IN 1..12 LOOP
    n := (i-1)%6+1; estado := estados[n]; cliente := md5('arise-cliente-'||n)::uuid;
    contrato := md5('arise-contrato-'||n)::uuid;
    dron := CASE WHEN n IN (1,5) THEN NULL WHEN n=2 THEN md5('arise-dron-'||(i/7+1))::uuid
      WHEN n=3 THEN md5('arise-dron-'||(i/7+3))::uuid ELSE md5('arise-dron-'||(5+i%2))::uuid END;
    paquete := md5('arise-paquete-'||i)::uuid;
    ruta := CASE n WHEN 1 THEN ARRAY['REGISTRADO'] WHEN 2 THEN ARRAY['REGISTRADO','ASIGNADO']
      WHEN 3 THEN ARRAY['REGISTRADO','ASIGNADO','EN_TRANSITO']
      WHEN 4 THEN ARRAY['REGISTRADO','ASIGNADO','EN_TRANSITO','ENTREGADO']
      WHEN 5 THEN ARRAY['REGISTRADO','CANCELADO']
      ELSE ARRAY['REGISTRADO','ASIGNADO','EN_TRANSITO','DEVUELTO'] END;
    instante := base-make_interval(days=>13-i)+interval '8 hours';
    INSERT INTO app.paquetes(id,codigo,descripcion,direccion_entrega,estado,cliente_id,contrato_id,dron_id,
      creado_en,actualizado_en,estado_actualizado_en,estado_actualizado_por,actualizado_por)
    VALUES(paquete,'ENV-2026-'||lpad(i::text,4,'0'),descripciones[n],
      'Carrera '||(20+i)||' # '||(50+i)||'-15, Bogotá',estado,cliente,contrato,dron,
      instante,instante,instante+make_interval(mins=>30*(array_length(ruta,1)-1)),actor,actor);
    FOR j IN 1..array_length(ruta,1) LOOP
      anterior := CASE WHEN j=1 THEN NULL ELSE ruta[j-1] END;
      INSERT INTO app.historial_estados_paquete(paquete_id,secuencia,estado_anterior,estado_nuevo,dron_id,observacion,registrado_en,registrado_por)
      VALUES(paquete,j,anterior,ruta[j],CASE WHEN ruta[j]='ASIGNADO' THEN dron END,
        CASE ruta[j] WHEN 'REGISTRADO' THEN 'Envío recibido en el centro de distribución.'
        WHEN 'ASIGNADO' THEN 'Dron asignado tras revisión de disponibilidad.'
        WHEN 'EN_TRANSITO' THEN 'Salida del centro de distribución.' WHEN 'ENTREGADO' THEN 'Entrega confirmada en destino.'
        WHEN 'DEVUELTO' THEN 'Destinatario ausente; retorno al centro de distribución.' ELSE 'Solicitud cancelada por el cliente antes del despacho.' END,
        instante+make_interval(mins=>30*(j-1)),actor);
    END LOOP;
  END LOOP;

  FOR i IN 1..8 LOOP
    articulo := md5('arise-articulo-'||i)::uuid;
    INSERT INTO app.articulos_inventario(id,codigo,nombre,categoria,unidad,stock_minimo,existencia,actualizado_por)
    VALUES(articulo,'INV-'||lpad(i::text,4,'0'),articulos[i],categorias[i],unidades[i],minimos[i],existencias[i],actor);
    INSERT INTO app.movimientos_inventario(articulo_id,tipo,cantidad,existencia_anterior,existencia_resultante,observacion,registrado_en,registrado_por)
    VALUES(articulo,'ENTRADA',existencias[i]+4,0,existencias[i]+4,'Recepción de abastecimiento del proveedor.',base-interval '7 days',actor),
      (articulo,'SALIDA',4,existencias[i]+4,existencias[i],'Consumo para operación y mantenimiento de la flota.',base-interval '2 days',actor);
  END LOOP;

  IF EXISTS (SELECT 1 FROM app.paquetes p JOIN app.contratos c ON c.id=p.contrato_id WHERE c.cliente_id<>p.cliente_id)
    OR EXISTS (SELECT 1 FROM app.paquetes p WHERE p.estado<>(SELECT h.estado_nuevo FROM app.historial_estados_paquete h WHERE h.paquete_id=p.id ORDER BY secuencia DESC LIMIT 1))
    OR EXISTS (SELECT 1 FROM app.articulos_inventario a WHERE a.existencia<>(SELECT m.existencia_resultante FROM app.movimientos_inventario m WHERE m.articulo_id=a.id ORDER BY registrado_en DESC LIMIT 1))
    OR (SELECT count(*) FROM app.clientes)<>27 THEN
    RAISE EXCEPTION 'Los datos de presentacion no superaron las comprobaciones de integridad';
  END IF;
END $$;
