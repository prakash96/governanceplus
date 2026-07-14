package com.governanceplus.reviewermule.ruleengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

/**
 * Resolves local "#/a/b/c" JSON Pointer $ref nodes (the OpenAPI/JSON Schema convention used by
 * requestBody/response schemas and parameters pointing into components.*) into the node they
 * point at, so JSONPath-based rules see the actual schema/parameter instead of a bare
 * {"$ref": "..."} stub. Jayway JsonPath has no idea what $ref means — this runs once, up front,
 * producing a fully-dereferenced COPY of the parsed spec, before any rule's JSONPath is evaluated.
 *
 * Only resolves refs local to this same document (starting with "#/") — a ref into another file
 * (e.g. "common.yaml#/...") is left untouched. Cyclical refs (e.g. a recursive schema containing
 * itself via $ref) are detected via the in-progress pointer stack and left unexpanded at the point
 * of recursion, rather than resolved infinitely.
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
            return fallback;
        }
        JsonNode target = pointerTarget(pointer);
        if (target == null) {
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
        JsonNode target = root.at(pointer.substring(1));
        return target.isMissingNode() ? null : target;
    }
}
