import type { MockedObject } from 'vitest';

/**
 * Create a Vitest mock object for a given type.
 *
 * This helper encapsulates the cast required when manually building a partial
 * mock of an Angular service or other class. Vitest's `MockedObject<T>` type
 * expects every method and property to be present, so a plain object literal
 * cannot be assigned without an assertion. Use this helper to keep the cast in
 * one place and make the test code read like a normal mock factory.
 */
export function createMock<T extends object>(
    overrides: { [K in keyof T]?: any } = {}
): MockedObject<T> {
    return overrides as unknown as MockedObject<T>;
}
