import { EVENT_SCHEMAS } from './schema.ts';
import type { EventSchema, FieldRule } from './schema.ts';

export class PrivacyGate {
  /**
   * Sanitizes any outbound event against the strict allowlist schema for its event type.
   * - Drops all unrecognized / unknown fields.
   * - Validates field types strictly.
   * - Rejects any string longer than maxStringLength (default 64) unless in allowedValues.
   * - Rejects string values that match prohibited path or secret patterns.
   * - Returns null if the event type is unknown or invalid.
   */
  static sanitize(rawEvent: unknown): Record<string, unknown> | null {
    if (!rawEvent || typeof rawEvent !== 'object' || Array.isArray(rawEvent)) {
      return null;
    }

    const event = rawEvent as Record<string, unknown>;
    const type = (event.type || event.kind) as string;

    if (!type || typeof type !== 'string') {
      return null;
    }

    const schema: EventSchema | undefined = EVENT_SCHEMAS[type];
    if (!schema) {
      return null;
    }

    const sanitized: Record<string, unknown> = {};

    for (const [key, rule] of Object.entries(schema)) {
      if (key in event) {
        const val = event[key];
        const cleanedVal = this.validateAndCleanValue(val, rule);
        if (cleanedVal !== undefined) {
          sanitized[key] = cleanedVal;
        }
      }
    }

    return sanitized;
  }

  private static validateAndCleanValue(val: unknown, rule: FieldRule): unknown {
    if (val === null || val === undefined) {
      return undefined;
    }

    switch (rule.type) {
      case 'number':
        if (typeof val === 'number' && Number.isFinite(val)) {
          return val;
        }
        return undefined;

      case 'boolean':
        if (typeof val === 'boolean') {
          return val;
        }
        return undefined;

      case 'string':
        if (typeof val !== 'string') {
          return undefined;
        }

        // Check if value is in enum allowedValues
        if (rule.allowedValues && rule.allowedValues.length > 0) {
          if (rule.allowedValues.includes(val)) {
            return val;
          }
          return undefined; // Value not in allowed category enum
        }

        // Enforce length limit (default max 64)
        const maxLen = rule.maxStringLength ?? 64;
        if (val.length > maxLen) {
          return undefined;
        }

        // Privacy safety check: Reject strings resembling file paths, urls, or secrets
        if (this.containsLeakPatterns(val)) {
          return undefined;
        }

        return val;

      case 'object':
        if (typeof val === 'object' && !Array.isArray(val) && rule.properties) {
          const nestedCleaned: Record<string, unknown> = {};
          const obj = val as Record<string, unknown>;
          for (const [propKey, propRule] of Object.entries(rule.properties)) {
            if (propKey in obj) {
              const nestedVal = this.validateAndCleanValue(obj[propKey], propRule);
              if (nestedVal !== undefined) {
                nestedCleaned[propKey] = nestedVal;
              }
            }
          }
          return nestedCleaned;
        }
        return undefined;

      default:
        return undefined;
    }
  }

  /**
   * Checks for obvious leaks like path separators, URLs, or command line invocation markers.
   */
  private static containsLeakPatterns(str: string): boolean {
    if (str.includes('/') || str.includes('\\')) return true;
    if (str.startsWith('http://') || str.startsWith('https://')) return true;
    if (str.includes('password') || str.includes('secret') || str.includes('token')) return true;
    return false;
  }
}
