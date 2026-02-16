"""
Market Basket Analysis API endpoint for Vercel.
Pure Python implementation - no pandas/numpy/mlxtend to stay under 250MB.
"""

import io
import csv
import json
import base64
import math
from itertools import combinations
from collections import defaultdict
from http.server import BaseHTTPRequestHandler


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

        if content.endswith(b"\r\n"):
            content = content[:-2]

        if 'name="csv_file"' in headers or 'name="csv_file";' in headers:
            file_data = content
            if "filename=" in headers:
                fn_part = headers.split("filename=")[1]
                file_name = fn_part.split('"')[1] if '"' in fn_part else fn_part.split(";")[0].strip()
        elif "name=" in headers:
            field_name = headers.split('name="')[1].split('"')[0]
            fields[field_name] = content.decode("utf-8").strip()

    return fields, file_data, file_name


# ---------------------------------------------------------------------------
# Pure-Python Apriori implementation
# ---------------------------------------------------------------------------

def _get_frequent_1_itemsets(transactions, min_support_count):
    """Find all items that meet min support."""
    counts = defaultdict(int)
    for txn in transactions:
        for item in txn:
            counts[frozenset([item])] += 1
    return {k: v for k, v in counts.items() if v >= min_support_count}


def _generate_candidates(prev_frequent, k):
    """Generate candidate itemsets of size k from frequent itemsets of size k-1."""
    items = list(prev_frequent)
    candidates = set()
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            union = items[i] | items[j]
            if len(union) == k:
                # Prune: all (k-1)-subsets must be frequent
                valid = True
                for item in union:
                    subset = union - frozenset([item])
                    if subset not in prev_frequent:
                        valid = False
                        break
                if valid:
                    candidates.add(union)
    return candidates


def apriori(transactions, min_support):
    """Run Apriori algorithm. Returns dict {frozenset: support_count}."""
    n = len(transactions)
    min_support_count = max(1, math.ceil(min_support * n))

    # Convert transactions to frozensets for fast lookup
    txn_sets = [frozenset(t) for t in transactions]

    # k=1
    freq = _get_frequent_1_itemsets(transactions, min_support_count)
    all_frequent = dict(freq)

    k = 2
    prev_frequent = freq
    while prev_frequent:
        candidates = _generate_candidates(prev_frequent, k)
        if not candidates:
            break

        counts = defaultdict(int)
        for txn in txn_sets:
            for cand in candidates:
                if cand.issubset(txn):
                    counts[cand] += 1

        current_frequent = {c: cnt for c, cnt in counts.items() if cnt >= min_support_count}
        all_frequent.update(current_frequent)
        prev_frequent = current_frequent
        k += 1

    return all_frequent, n


def generate_rules(frequent_itemsets, n_transactions, min_confidence, min_lift):
    """Generate association rules from frequent itemsets."""
    # Build support lookup
    support_map = {fs: count / n_transactions for fs, count in frequent_itemsets.items()}

    rules = []
    for itemset, count in frequent_itemsets.items():
        if len(itemset) < 2:
            continue
        itemset_support = count / n_transactions

        # Generate all non-empty proper subsets as antecedents
        items = list(itemset)
        for i in range(1, len(items)):
            for antecedent_tuple in combinations(items, i):
                antecedent = frozenset(antecedent_tuple)
                consequent = itemset - antecedent

                if not consequent:
                    continue

                ant_support = support_map.get(antecedent, 0)
                con_support = support_map.get(consequent, 0)

                if ant_support == 0 or con_support == 0:
                    continue

                confidence = itemset_support / ant_support
                lift = confidence / con_support if con_support > 0 else 0
                leverage = itemset_support - (ant_support * con_support)
                if confidence < 1.0:
                    conviction = (1 - con_support) / (1 - confidence)
                else:
                    conviction = float('inf')

                if confidence >= min_confidence and lift >= min_lift:
                    rules.append({
                        "antecedents": " + ".join(sorted(antecedent)),
                        "consequents": " + ".join(sorted(consequent)),
                        "support": round(itemset_support, 6),
                        "confidence": round(confidence, 6),
                        "lift": round(lift, 6),
                        "leverage": round(leverage, 6),
                        "conviction": round(conviction, 6) if conviction != float('inf') else 999999.0,
                    })

    rules.sort(key=lambda r: r["lift"], reverse=True)
    return rules


# ---------------------------------------------------------------------------
# Main analysis logic
# ---------------------------------------------------------------------------

def run_analysis(csv_data, columna_items, tipo_transaccion_filtro,
                 min_support, min_confidence, min_lift):
    """Run Market Basket Analysis and return results."""
    logs = []

    def log(msg):
        logs.append(msg)

    log("INICIANDO ANALISIS DE CESTA DE MERCADO")
    log("=" * 60)

    # PASO 1: Load CSV
    log("\nPASO 1: Cargando datos...")
    text = csv_data.decode("utf-8", errors="replace")
    reader = csv.DictReader(io.StringIO(text))
    rows = list(reader)
    columns = reader.fieldnames or []

    log(f"  Datos cargados: {len(rows)} filas, {len(columns)} columnas")
    log(f"  Columnas disponibles: {', '.join(columns)}")

    # Validate columns
    required_cols = ["transaction_id", columna_items, "tipo_transaccion"]
    missing = [c for c in required_cols if c not in columns]
    if missing:
        return {
            "success": False,
            "error": f"Columnas faltantes en el CSV: {', '.join(missing)}. "
                     f"Columnas disponibles: {', '.join(columns)}",
            "logs": logs,
        }

    # PASO 2: Clean data
    log("\nPASO 2: Limpieza de datos...")
    total_before = len(rows)
    unique_txn_before = len(set(r["transaction_id"] for r in rows))
    unique_items_before = len(set(r[columna_items] for r in rows))
    not_set_count = sum(1 for r in rows if r[columna_items] == "(not set)")

    log(f"  ANTES DE LA LIMPIEZA:")
    log(f"  - Total de registros: {total_before}")
    log(f"  - Total de transacciones: {unique_txn_before}")
    log(f"  - Total de productos unicos en '{columna_items}': {unique_items_before}")
    log(f"  - Registros con '(not set)': {not_set_count}")

    rows = [r for r in rows
            if r[columna_items] != "(not set)"
            and r["transaction_id"].strip()
            and r[columna_items].strip()]

    unique_txn_after = len(set(r["transaction_id"] for r in rows))
    unique_items_after = len(set(r[columna_items] for r in rows))

    log(f"  DESPUES DE LA LIMPIEZA:")
    log(f"  - Total de registros: {len(rows)}")
    log(f"  - Total de transacciones: {unique_txn_after}")
    log(f"  - Total de productos unicos en '{columna_items}': {unique_items_after}")
    log(f"  - Registros eliminados: {total_before - len(rows)}")

    # Available transaction types
    available_types = sorted(set(r["tipo_transaccion"] for r in rows))
    log(f"\n  Tipos de transaccion disponibles: {available_types}")

    # PASO 3: Prepare transactions
    log(f"\nPASO 3: Preparando transacciones (filtro: '{tipo_transaccion_filtro}')...")
    log(f"  Usando columna: '{columna_items}'")

    filtered = [r for r in rows if r["tipo_transaccion"] == tipo_transaccion_filtro]
    if not filtered:
        return {
            "success": False,
            "error": f"No se encontraron registros con tipo_transaccion = '{tipo_transaccion_filtro}'. "
                     f"Tipos disponibles: {available_types}",
            "logs": logs,
        }

    # Group by transaction_id
    grouped = defaultdict(list)
    for r in filtered:
        grouped[r["transaction_id"]].append(r[columna_items])

    # Only keep transactions with more than 1 item
    transactions = [items for items in grouped.values() if len(items) > 1]
    log(f"  Total de transacciones preparadas: {len(transactions)}")

    if not transactions:
        return {
            "success": False,
            "error": "No se encontraron transacciones con mas de 1 item despues del filtrado.",
            "logs": logs,
        }

    if len(transactions) >= 1:
        log(f"  Ejemplo de transaccion 1: {transactions[0]}")
    if len(transactions) >= 2:
        log(f"  Ejemplo de transaccion 2: {transactions[1]}")

    # PASO 4 & 5: Apriori
    log(f"\nPASO 4-5: Aplicando algoritmo Apriori (soporte minimo: {min_support})...")
    frequent_itemsets, n_txn = apriori(transactions, min_support)
    log(f"  Conjuntos frecuentes encontrados: {len(frequent_itemsets)}")

    if not frequent_itemsets:
        return {
            "success": False,
            "error": "No se encontraron conjuntos frecuentes. Intenta reducir el soporte minimo.",
            "logs": logs,
        }

    # PASO 6: Association rules
    log(f"\nPASO 6: Generando reglas de asociacion...")
    log(f"  - Confianza minima: {min_confidence}")
    log(f"  - Lift minimo: {min_lift}")

    rules = generate_rules(frequent_itemsets, n_txn, min_confidence, min_lift)
    log(f"  Reglas de asociacion encontradas: {len(rules)}")

    if not rules:
        return {
            "success": False,
            "error": "No se encontraron reglas con los parametros especificados. "
                     "Intenta reducir min_confidence o min_support.",
            "logs": logs,
        }

    # Generate Excel with openpyxl
    from openpyxl import Workbook
    wb = Workbook()
    ws = wb.active
    ws.title = "Association Rules"

    export_cols = ["antecedents", "consequents", "support", "confidence", "lift", "leverage", "conviction"]
    ws.append(export_cols)
    for rule in rules:
        ws.append([rule[c] for c in export_cols])

    excel_buffer = io.BytesIO()
    wb.save(excel_buffer)
    excel_buffer.seek(0)
    excel_b64 = base64.b64encode(excel_buffer.read()).decode("utf-8")

    preview = rules[:50]

    log("\n" + "=" * 60)
    log("ANALISIS COMPLETADO")
    log(f"  Total de reglas encontradas: {len(rules)}")
    log("=" * 60)

    return {
        "success": True,
        "total_rules": len(rules),
        "preview": preview,
        "excel_base64": excel_b64,
        "logs": logs,
        "columns": export_cols,
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
                self._send_json(400, {"success": False, "error": "No se recibio archivo CSV"})
                return

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
