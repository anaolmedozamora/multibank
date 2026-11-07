-- ============================================================
-- V1: esquema inicial
-- Convención de importes: NUMERIC(14,2). En movimientos bancarios,
-- negativo = cargo (salida), positivo = abono (entrada).
-- ============================================================

CREATE TABLE empresa (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    cif         VARCHAR(20),
    creada_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     BIGINT NOT NULL REFERENCES empresa(id),
    email          VARCHAR(200) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    rol            VARCHAR(20) NOT NULL DEFAULT 'USUARIO'
                   CHECK (rol IN ('ADMIN', 'USUARIO', 'LECTURA')),
    activo         BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cuenta_bancaria (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT NOT NULL REFERENCES empresa(id),
    nombre      VARCHAR(100) NOT NULL,          -- ej. "Cuenta principal"
    banco       VARCHAR(100),
    iban        VARCHAR(34),
    moneda      CHAR(3) NOT NULL DEFAULT 'EUR',
    activa      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (empresa_id, nombre)
);

CREATE TABLE proveedor (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT NOT NULL REFERENCES empresa(id),
    nombre      VARCHAR(150) NOT NULL,
    cif         VARCHAR(20),
    UNIQUE (empresa_id, nombre)
);

CREATE TABLE categoria (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT NOT NULL REFERENCES empresa(id),
    nombre      VARCHAR(100) NOT NULL,
    UNIQUE (empresa_id, nombre)
);

-- Gastos registrados (lo que la empresa "debe" o ha pagado)
CREATE TABLE gasto (
    id                 BIGSERIAL PRIMARY KEY,
    empresa_id         BIGINT NOT NULL REFERENCES empresa(id),
    fecha_gasto        DATE NOT NULL,            -- la semana se calcula a partir de esta fecha
    concepto           VARCHAR(250) NOT NULL,
    importe            NUMERIC(14,2) NOT NULL CHECK (importe > 0),
    proveedor_id       BIGINT REFERENCES proveedor(id),
    categoria_id       BIGINT REFERENCES categoria(id),
    cuenta_id          BIGINT REFERENCES cuenta_bancaria(id), -- cuenta prevista de pago
    estado             VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE'
                       CHECK (estado IN ('PENDIENTE', 'PAGADO')),
    fecha_vencimiento  DATE,
    fecha_pago         DATE,
    notas              TEXT,
    creado_por         BIGINT REFERENCES usuario(id),
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (estado = 'PAGADO' OR fecha_pago IS NULL)
);

CREATE INDEX idx_gasto_empresa_fecha ON gasto (empresa_id, fecha_gasto);
CREATE INDEX idx_gasto_empresa_estado ON gasto (empresa_id, estado);

-- Cada fichero subido (extracto o Excel de gastos)
CREATE TABLE importacion (
    id               BIGSERIAL PRIMARY KEY,
    empresa_id       BIGINT NOT NULL REFERENCES empresa(id),
    cuenta_id        BIGINT REFERENCES cuenta_bancaria(id),  -- solo para extractos
    tipo             VARCHAR(20) NOT NULL
                     CHECK (tipo IN ('EXTRACTO', 'EXCEL_GASTOS')),
    nombre_fichero   VARCHAR(255) NOT NULL,
    hash_fichero     CHAR(64) NOT NULL,                      -- SHA-256 del contenido
    filas_leidas     INTEGER NOT NULL DEFAULT 0,
    filas_nuevas     INTEGER NOT NULL DEFAULT 0,
    importado_por    BIGINT REFERENCES usuario(id),
    importado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, hash_fichero)                        -- evita subir el mismo fichero dos veces
);

-- Movimientos del extracto bancario
CREATE TABLE movimiento_bancario (
    id               BIGSERIAL PRIMARY KEY,
    cuenta_id        BIGINT NOT NULL REFERENCES cuenta_bancaria(id),
    importacion_id   BIGINT REFERENCES importacion(id),
    fecha_operacion  DATE NOT NULL,
    fecha_valor      DATE,
    concepto         VARCHAR(500),
    importe          NUMERIC(14,2) NOT NULL,                 -- negativo = cargo
    saldo            NUMERIC(14,2),
    huella           CHAR(64) NOT NULL,                      -- hash de fecha+importe+concepto+saldo(+orden)
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cuenta_id, huella)                               -- evita duplicados entre extractos solapados
);

CREATE INDEX idx_mov_cuenta_fecha ON movimiento_bancario (cuenta_id, fecha_operacion);

-- Conciliación: un movimiento puede cubrir varios gastos y un gasto
-- puede pagarse en varios movimientos (pagos parciales).
CREATE TABLE conciliacion (
    id               BIGSERIAL PRIMARY KEY,
    movimiento_id    BIGINT NOT NULL REFERENCES movimiento_bancario(id) ON DELETE CASCADE,
    gasto_id         BIGINT NOT NULL REFERENCES gasto(id) ON DELETE CASCADE,
    importe_aplicado NUMERIC(14,2) NOT NULL CHECK (importe_aplicado > 0),
    origen           VARCHAR(12) NOT NULL DEFAULT 'MANUAL'
                     CHECK (origen IN ('AUTOMATICA', 'MANUAL')),
    estado           VARCHAR(12) NOT NULL DEFAULT 'CONFIRMADA'
                     CHECK (estado IN ('SUGERIDA', 'CONFIRMADA')),
    confianza        NUMERIC(3,2) CHECK (confianza BETWEEN 0 AND 1),
    creada_por       BIGINT REFERENCES usuario(id),
    creada_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (movimiento_id, gasto_id)
);

-- Registro de quién hizo qué
CREATE TABLE auditoria (
    id           BIGSERIAL PRIMARY KEY,
    empresa_id   BIGINT NOT NULL REFERENCES empresa(id),
    usuario_id   BIGINT REFERENCES usuario(id),
    entidad      VARCHAR(50) NOT NULL,
    entidad_id   BIGINT,
    accion       VARCHAR(30) NOT NULL,
    detalle      JSONB,
    fecha        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auditoria_empresa_fecha ON auditoria (empresa_id, fecha DESC);