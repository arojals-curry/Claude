"""
Market Basket Analysis API endpoint for Vercel.
Receives a CSV file and configuration parameters,
runs Apriori algorithm and returns association rules as Excel.
"""

import io
import json
import base64
from http.server import BaseHTTPRequestHandler
import pandas as pd
import numpy as np
from mlxtend.frequent_patterns import apriori, association_rules
from mlxtend.preprocessing import TransactionEncoder
import warnings

warnings.filterwarnings("ignore")


def parse_multipart(body: bytes, content_type: str):
    """Parse multipart/form-data manually."""
    boundary = content_type.split("boundary=")[1].strip()
    if boundary.startswith('"') and boundary.endswith('"'):
        boundary = boundary[1:-1]

    parts = body.split(("--" + boundary).encode())
    fields = {}
    file_data = None
    file_name = None

    for part in parts:
        if not part or part == b"--\r\n" or part == b"--":
            continue

        if b"\r\n\r\n" not in part:
            continue

        header_section, _, content = part.partition(b"\r\n\r\n")
        headers = header_section.decode("utf-8", errors="replace")

        # Remove trailing \r\n
        if content.endswith(b"\r\n"):
            content = content[:-2]

        if 'name="csv_file"' in headers or 'name="csv_file";' in headers:
            file_data = content
            # Extract filename if present
            if "filename=" in headers:
                fn_part = headers.split("filename=")[1]
                file_name = fn_part.split('"')[1] if '"' in fn_part else fn_part.split(";")[0].strip()
        elif "name=" in headers:
            field_name = headers.split('name="')[1].split('"')[0]
            fields[field_name] = content.decode("utf-8").strip()

    return fields, file_data, file_name


def run_analysis(csv_data: bytes, columna_items: str, tipo_transaccion_filtro: str,
                 min_support: float, min_confidence: float, min_lift: float):
    """Run the Market Basket Analysis and return results."""
    logs = []

    def log(msg):
        logs.append(msg)

    log("INICIANDO ANÁLISIS DE CESTA DE MERCADO")
    log("=" * 60)

    # PASO 1: Load data
    log("\nPASO 1: Cargando datos...")
    df = pd.read_csv(io.BytesIO(csv_data))
    log(f"  Datos cargados: {df.shape[0]} filas, {df.shape[1]} columnas")
    log(f"  Columnas disponibles: {', '.join(df.columns.tolist())}")

    # Validate required columns
    required_cols = ["transaction_id", columna_items, "tipo_transaccion"]
    missing = [c for c in required_cols if c not in df.columns]
    if missing:
        return {
            "success": False,
            "error": f"Columnas faltantes en el CSV: {', '.join(missing)}. "
                     f"Columnas disponibles: {', '.join(df.columns.tolist())}",
            "logs": logs,
        }

    # PASO 2: Clean data
    log("\nPASO 2: Limpieza de datos...")
    log(f"  ANTES DE LA LIMPIEZA:")
    log(f"  - Total de registros: {len(df)}")
    log(f"  - Total de transacciones: {df['transaction_id'].nunique()}")
    log(f"  - Total de productos únicos en '{columna_items}': {df[columna_items].nunique()}")

    not_set_count = df[df[columna_items] == "(not set)"].shape[0]
    log(f"  - Registros con '(not set)': {not_set_count}")

    df = df[df[columna_items] != "(not set)"].copy()
    df = df.dropna(subset=["transaction_id", columna_items])

    valid_transactions = df.groupby("transaction_id").size() > 0
    valid_transaction_ids = valid_transactions[valid_transactions].index
    df = df[df["transaction_id"].isin(valid_transaction_ids)]

    log(f"  DESPUÉS DE LA LIMPIEZA:")
    log(f"  - Total de registros: {len(df)}")
    log(f"  - Total de transacciones: {df['transaction_id'].nunique()}")
    log(f"  - Total de productos únicos en '{columna_items}': {df[columna_items].nunique()}")
    log(f"  - Registros eliminados: {not_set_count}")

    # List available transaction types
    available_types = df["tipo_transaccion"].unique().tolist()
    log(f"\n  Tipos de transacción disponibles: {available_types}")

    # PASO 3: Prepare transactions
    log(f"\nPASO 3: Preparando transacciones (filtro: '{tipo_transaccion_filtro}')...")
    log(f"  Usando columna: '{columna_items}'")

    filtered_df = df[df["tipo_transaccion"] == tipo_transaccion_filtro]
    if len(filtered_df) == 0:
        return {
            "success": False,
            "error": f"No se encontraron registros con tipo_transaccion = '{tipo_transaccion_filtro}'. "
                     f"Tipos disponibles: {available_types}",
            "logs": logs,
        }

    grouped = filtered_df.groupby("transaction_id")[columna_items].apply(list)
    transactions = grouped.loc[lambda x: x.str.len() > 1].values.tolist()

    log(f"  Total de transacciones preparadas: {len(transactions)}")

    if len(transactions) == 0:
        return {
            "success": False,
            "error": "No se encontraron transacciones con más de 1 item después del filtrado.",
            "logs": logs,
        }

    if len(transactions) >= 1:
        log(f"  Ejemplo de transacción 1: {transactions[0]}")
    if len(transactions) >= 2:
        log(f"  Ejemplo de transacción 2: {transactions[1]}")

    # PASO 4: Binary encoding
    log("\nPASO 4: Codificando transacciones en formato binario...")
    te = TransactionEncoder()
    te_ary = te.fit(transactions).transform(transactions)
    df_encoded = pd.DataFrame(te_ary, columns=te.columns_)
    log(f"  Matriz binaria creada: {df_encoded.shape[0]} transacciones x {df_encoded.shape[1]} productos")

    # PASO 5: Apriori algorithm
    log(f"\nPASO 5: Aplicando algoritmo Apriori (soporte mínimo: {min_support})...")
    frequent_itemsets = apriori(df_encoded, min_support=min_support, use_colnames=True)
    log(f"  Conjuntos frecuentes encontrados: {len(frequent_itemsets)}")

    if len(frequent_itemsets) == 0:
        return {
            "success": False,
            "error": "No se encontraron conjuntos frecuentes. Intenta reducir el soporte mínimo.",
            "logs": logs,
        }

    # PASO 6: Association rules
    log(f"\nPASO 6: Generando reglas de asociación...")
    log(f"  - Confianza mínima: {min_confidence}")
    log(f"  - Lift mínimo: {min_lift}")

    rules = association_rules(frequent_itemsets, metric="confidence", min_threshold=min_confidence)
    rules = rules[rules["lift"] >= min_lift]
    rules = rules.sort_values("lift", ascending=False)

    log(f"  Reglas de asociación encontradas: {len(rules)}")

    if len(rules) == 0:
        return {
            "success": False,
            "error": "No se encontraron reglas con los parámetros especificados. "
                     "Intenta reducir min_confidence o min_support.",
            "logs": logs,
        }

    # Prepare export
    rules_export = rules.copy()
    rules_export["antecedents"] = rules_export["antecedents"].apply(lambda x: " + ".join(sorted(x)))
    rules_export["consequents"] = rules_export["consequents"].apply(lambda x: " + ".join(sorted(x)))

    export_cols = ["antecedents", "consequents", "support", "confidence", "lift", "leverage", "conviction"]
    available_export_cols = [c for c in export_cols if c in rules_export.columns]
    rules_export = rules_export[available_export_cols]

    for col in available_export_cols:
        if col not in ("antecedents", "consequents"):
            rules_export[col] = rules_export[col].round(6)

    # Generate Excel
    excel_buffer = io.BytesIO()
    rules_export.to_excel(excel_buffer, index=False, sheet_name="Association Rules", engine="openpyxl")
    excel_buffer.seek(0)
    excel_b64 = base64.b64encode(excel_buffer.read()).decode("utf-8")

    # Generate preview (top 50 rules as JSON)
    preview = rules_export.head(50).to_dict(orient="records")

    log("\n" + "=" * 60)
    log("ANÁLISIS COMPLETADO")
    log(f"  Total de reglas encontradas: {len(rules_export)}")
    log("=" * 60)

    return {
        "success": True,
        "total_rules": len(rules_export),
        "preview": preview,
        "excel_base64": excel_b64,
        "logs": logs,
        "columns": available_export_cols,
    }


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            content_type = self.headers.get("Content-Type", "")

            if "multipart/form-data" not in content_type:
                self._send_json(400, {"success": False, "error": "Se requiere multipart/form-data"})
                return

            fields, file_data, file_name = parse_multipart(body, content_type)

            if file_data is None:
                self._send_json(400, {"success": False, "error": "No se recibió archivo CSV"})
                return

            # Extract parameters with defaults
            columna_items = fields.get("columna_items", "tipo_prenda_concat")
            tipo_transaccion_filtro = fields.get("tipo_transaccion_filtro", "Solo Bebe")
            min_support = float(fields.get("min_support", "0.001"))
            min_confidence = float(fields.get("min_confidence", "0.4"))
            min_lift = float(fields.get("min_lift", "1.0"))

            result = run_analysis(
                csv_data=file_data,
                columna_items=columna_items,
                tipo_transaccion_filtro=tipo_transaccion_filtro,
                min_support=min_support,
                min_confidence=min_confidence,
                min_lift=min_lift,
            )

            self._send_json(200, result)

        except Exception as e:
            self._send_json(500, {"success": False, "error": str(e)})

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def _send_json(self, status_code, data):
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data, default=str).encode("utf-8"))
