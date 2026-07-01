import { beforeEach, vi } from 'vitest';

const createMockStorage = () => {
    const store: Record<string, string> = {};
    return {
        getItem: vi.fn((key: string) => store[key] ?? null),
        setItem: vi.fn((key: string, value: string) => {
            store[key] = value;
        }),
        removeItem: vi.fn((key: string) => {
            delete store[key];
        }),
        clear: vi.fn(() => {
            for (const key in store) {
                delete store[key];
            }
        }),
        key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
        get length() {
            return Object.keys(store).length;
        },
    };
};

const localStorageMock = createMockStorage();
const sessionStorageMock = createMockStorage();

Object.defineProperty(globalThis, 'localStorage', {
    value: localStorageMock,
    configurable: true,
    writable: true,
});

Object.defineProperty(globalThis, 'sessionStorage', {
    value: sessionStorageMock,
    configurable: true,
    writable: true,
});

beforeEach(() => {
    localStorageMock.clear();
    localStorageMock.getItem.mockClear();
    localStorageMock.setItem.mockClear();
    localStorageMock.removeItem.mockClear();
    localStorageMock.key.mockClear();

    sessionStorageMock.clear();
    sessionStorageMock.getItem.mockClear();
    sessionStorageMock.setItem.mockClear();
    sessionStorageMock.removeItem.mockClear();
    sessionStorageMock.key.mockClear();
});
