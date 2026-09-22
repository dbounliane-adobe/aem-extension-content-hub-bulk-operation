/*
 * Content Hub proxy action.
 *
 * The Assets View modal cannot POST directly to the AEM author: the browser
 * issues a CORS preflight that AEM's default policy rejects. This web action
 * runs on Adobe I/O Runtime (same trust domain as the SPA, so CORS-friendly)
 * and forwards the request to the AEM Content Hub servlet server-to-server,
 * where CORS does not apply. Mirrors the "3D viewer" fetchAsset proxy pattern.
 */
const fetch = require('node-fetch')
const { Core } = require('@adobe/aio-sdk')
const { errorResponse, getBearerToken, stringParameters, checkMissingRequestInputs } = require('../utils')

async function main (params) {
  const logger = Core.Logger('main', { level: params.LOG_LEVEL || 'info' })

  try {
    logger.debug(stringParameters(params))

    const requiredParams = ['aemHost', 'path', 'status', 'activationTarget']
    const requiredHeaders = ['Authorization']
    const errorMessage = checkMissingRequestInputs(params, requiredParams, requiredHeaders)
    if (errorMessage) {
      return errorResponse(400, errorMessage, logger)
    }

    const token = getBearerToken(params)

    const host = String(params.aemHost).replace(/\/+$/, '')
    const path = String(params.path)
    const endpoint = `${host}${path}.contenthub.json`

    const body = new URLSearchParams({
      status: params.status,
      activationTarget: params.activationTarget,
      recursive: String(params.recursive === true || params.recursive === 'true')
    }).toString()

    logger.info(`Forwarding Content Hub request to ${endpoint}`)
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body
    })

    const text = await res.text()
    let parsed
    try {
      parsed = JSON.parse(text)
    } catch (_) {
      parsed = { raw: text }
    }

    logger.info(`AEM responded ${res.status}`)
    return {
      statusCode: res.status,
      body: parsed
    }
  } catch (error) {
    logger.error(error)
    return errorResponse(500, 'server error: ' + error.message, logger)
  }
}

exports.main = main
