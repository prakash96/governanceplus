package com.governanceplus.reviewer.ruleengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

/**
 * Resolves local "#/a/b/c" JSON Pointer $ref nodes (the OpenAPI/JSON Schema
 * convention used by requestBody/response schemas and parameters pointing
 * into components.*) into the node they point at, so JSONPath-based rules
 * see the actual schema/parameter instead of a bare {"$ref": "..."} stub.
 * Jayway JsonPath has no idea what $ref means — this runs once, up front,
 * producing a fully-dereferenced COPY of the parsed spec, before any rule's
 * JSONPath is evaluated against it.
 *
 * Only resolves refs local to this same document (starting with "#/") — a
 * ref into another file (e.g. "common.yaml#/...") is left untouched, since
 * resolving that would require loading and parsing a second file this
 * evaluator doesn't have. Per OpenAPI 3.0 semantics, a $ref replaces the
 * whole object it's found on — sibling keys alongside "$ref" are discarded,
 * matching how compliant OpenAPI 3.0 tooling treats them (OpenAPI 3.1's
 * JSON-Schema-2020-12-style "$ref with siblings" isn't specially handled).
 *
 * Cyclical refs (e.g. a recursive "Category" schema containing itself via
 * $ref) are detected via the in-progress pointer stack and left unexpanded
 * at the point of recursion, rather than resolved infinitely.
 */
final class SwaggerRefResolver {

    private final JsonNode root;

    SwaggerRefResolver(JsonNode root) {
        this.root = root;
    }

    JsonNode resolve() {
        return resolve(root, new ArrayDeque<>());
    }

    private JsonNode resolve(JsonNode node, Deque<String> inProgress) {
        if (node == null || !(node.isObject() || node.isArray())) {
            return node;
        }

        if (node.isObject()) {
            JsonNode refNode = node.get("$ref");
            if (refNode != null && refNode.isTextual() && refNode.asText().startsWith("#/")) {
                return resolveRef(refNode.asText(), inProgress, node);
            }

            ObjectNode result = ((ObjectNode) node).objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                result.set(entry.getKey(), resolve(entry.getValue(), inProgress));
            }
            return result;
        }

        ArrayNode result = ((ArrayNode) node).arrayNode();
        for (JsonNode item : node) {
            result.add(resolve(item, inProgress));
        }
        return result;
    }

    private JsonNode resolveRef(String pointer, Deque<String> inProgress, JsonNode fallback) {
        if (inProgress.contains(pointer)) {
            // Cycle: leave unexpanded rather than recurse forever.
            return fallback;
        }
        JsonNode target = pointerTarget(pointer);
        if (target == null) {
            // Dangling ref — leave as-is rather than fail the whole review over it.
            return fallback;
        }

        inProgress.push(pointer);
        try {
            return resolve(target, inProgress);
        } finally {
            inProgress.pop();
        }
    }

    private JsonNode pointerTarget(String pointer) {
        // pointer looks like "#/components/schemas/Order"; JsonNode#at() takes
        // a JSON Pointer with a leading "/" and no "#".
        JsonNode target = root.at(pointer.substring(1));
        return target.isMissingNode() ? null : target;
    }
}
