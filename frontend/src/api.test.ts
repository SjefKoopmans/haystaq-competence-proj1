import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './api';

/**
 * Karakteriseringstests voor de HTTP-laag. Ze leggen vast hoe de frontend zich
 * nu gedraagt bij de nietszeggende foutmeldingen van de API. Gaat het
 * foutcontract in fase 1 naar RFC 9457, dan vallen deze tests om - en dat is
 * precies de bedoeling: de frontend parseert `payload.error` en breekt dus mee.
 */

function mockFetch(status: number, body: string) {
  const spy = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(body)
  } as Response);
  vi.stubGlobal('fetch', spy);
  return spy;
}

/** Vangt de fout op én bewijst dat de aanroep daadwerkelijk faalde. */
async function captureError(action: () => Promise<unknown>): Promise<ApiError> {
  try {
    await action();
  } catch (error) {
    return error as ApiError;
  }
  throw new Error('verwachtte een ApiError, maar de aanroep slaagde');
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api', () => {
  it('should_prefix_path_with_api_when_calling_get', async () => {
    const fetchSpy = mockFetch(200, '[]');

    await api.get('/employees');

    expect(fetchSpy).toHaveBeenCalledWith('/api/employees', expect.objectContaining({
      method: 'GET',
      headers: { 'content-type': 'application/json' }
    }));
  });

  it('should_send_empty_object_when_post_without_body', async () => {
    const fetchSpy = mockFetch(200, '{}');

    await api.post('/admin/reset');

    expect(fetchSpy.mock.calls[0][1]).toMatchObject({ method: 'POST', body: '{}' });
  });

  it('should_return_null_when_response_body_is_empty', async () => {
    mockFetch(200, '');

    await expect(api.del('/time-entries/1')).resolves.toBeNull();
  });

  it('should_throw_ApiError_with_status_and_message_when_rejected', async () => {
    mockFetch(400, JSON.stringify({ error: 'invalid input' }));

    await expect(api.post('/employees', { code: 'x' })).rejects.toMatchObject({
      status: 400,
      message: 'invalid input'
    });
  });

  it('should_carry_reference_when_server_returns_internal_error', async () => {
    mockFetch(500, JSON.stringify({ error: 'internal error', ref: 'a1b2c3d4' }));

    const error = await captureError(() => api.get('/reports/summary'));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.describe()).toBe('HTTP 500 - internal error - ref a1b2c3d4');
  });

  it('should_fall_back_to_generic_message_when_error_field_is_missing', async () => {
    mockFetch(409, '{}');

    const error = await captureError(() => api.post('/timesheets/1/submit'));

    expect(error.message).toBe('onbekende fout');
    expect(error.reference).toBeUndefined();
  });
});
