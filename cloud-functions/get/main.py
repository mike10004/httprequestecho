import sys
from flask import abort
import json
from collections.abc import Sequence


_KEY_HEADERS = 'headers'
_KEY_METHOD = 'method'
_KEY_QUERY = 'queryParameters'
_KEY_ORIGIN = 'origin'


def _is_collection(thing):
    return isinstance(thing, Sequence) and not isinstance(thing, (str, bytes, bytearray))


def _make_multivalued_map(dictlike):
    result = {}
    for k, v in dictlike:
        if _is_collection(v):
            v = list(v)
        else:
            v = [v]
        result[k] = v
    return result


def _make_response(request):
    rsp = {}
    rsp[_KEY_HEADERS] = _make_multivalued_map(request.headers)
    rsp[_KEY_QUERY] = _make_multivalued_map(request.args)
    rsp[_KEY_METHOD] = request.method
    return rsp


def _make_response_json(request):
    response = _make_response(request)
    response_json = json.dumps(response, indent=2)
    return response_json


def http_get(request):
    """HTTP Cloud Function.
    Args:
        request (flask.Request): The request object.
        <http://flask.pocoo.org/docs/0.12/api/#flask.Request>
    Returns:
        The response text, or any set of values that can be turned into a
        Response object using `make_response`
        <http://flask.pocoo.org/docs/0.12/api/#flask.Flask.make_response>.
    """
    if request.method == 'GET':
        text = _make_response_json(request)
        headers = {
            'content-type': 'application/json'
        }
        return (text, 200, headers)
    else:
        return abort(405)
