##
# Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this
# software and associated documentation files (the "Software"), to deal in the Software
# without restriction, including without limitation the rights to use, copy, modify,
# merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
# INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
# HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
##

import sys
import requests
import inspect
import json
from random import randint

data = dict()
BASE_URL = 'http://localhost:3000/orders'

NUM_ORDERS = int(sys.argv[1])

def test_create_table():
  print('TEST: CREATE TABLE')
  r = requests.post('http://localhost:3000/_create_orders_table')
  try:
    response = r.text
    print(response)
    print('\nAttempting to parse JSON...')
    print(json.loads(response), end='\n\n')
  except Exception as e:
    frame = inspect.currentframe()
    print(inspect.getframeinfo(frame).function + ' failed.')
    print(e)


def test_create_order():
  print('\n\nTEST: CREATING ORDER')
  r = requests.post(BASE_URL, data=json.dumps({
                    "customerId": randint(1, 50),
                    "preTaxAmount": randint(1, 50),
                    "postTaxAmount": randint(1, 50)}))
  try:
    response = r.text
    print(response)
    print('\nAttempting to parse JSON...')
    print(json.loads(response), end='\n\n')
  except Exception as e:
    frame = inspect.currentframe()
    print(inspect.getframeinfo(frame).function + ' failed.')
    print(e)


def test_get_orders():
  print('\n\nTEST: GETTING ALL ORDERS')
  r = requests.get(BASE_URL)
  try:
    response = r.text
    print(response)
    print('\nAttempting to parse JSON...')
    global data
    data = json.loads(response)
    print(data, end='\n\n')
  except Exception as e:
    frame = inspect.currentframe()
    print(inspect.getframeinfo(frame).function + ' failed.')
    print(e)


def test_get_order():
  print('\n\nTEST: GETTING SPECIFIC ORDERS')
  if(not data):
    print('Nothing!')
    return
  orders = data['orders']
  for i in range(len(orders)):
    r = requests.get(BASE_URL + '/' + orders[i]['orderId'])
    try:
      response = r.text
      print(response)
      print('\nAttempting to parse JSON...')
      print(json.loads(response), end='\n\n')
    except Exception as e:
      frame = inspect.currentframe()
      print(inspect.getframeinfo(frame).function + ' failed.')
      print(e)


def test_update_order():
  print('\n\nTEST: UPDATING SPECIFIC ORDERS')
  if(not data):
    return
  orders = data['orders']
  for i in range(len(orders)):
    payload = {
      "customerId": randint(5000, 6000),
      "preTaxAmount": randint(5000, 6000),
      "postTaxAmount": randint(5000, 6000),
      "version": orders[i]['version']
    }
    r = requests.post(BASE_URL + '/' + orders[i]['orderId'], data=json.dumps(payload))
    try:
      response = r.text
      print(response, r.status_code)
      print('\nAttempting to parse JSON...')
      print(json.loads(response), end='\n\n')
    except Exception as e:
      frame = inspect.currentframe()
      print(inspect.getframeinfo(frame).function + ' failed.')
      print(e)


def test_delete_order():
  print('\n\nTEST: DELETING SPECIFIC ORDERS')
  if(not data):
    return
  orders = data['orders']
  for i in range(len(orders)):
    r = requests.delete(BASE_URL + '/' + orders[i]['orderId'])
    try:
      response = r.text
      print(response)
      print('\nAttempting to parse JSON...')
      print(json.loads(response), end='\n\n')
    except Exception as e:
      frame = inspect.currentframe()
      print(inspect.getframeinfo(frame).function + ' failed.')
      print(e)


test_create_table()

for i in range(NUM_ORDERS):
  test_create_order()

test_get_orders()
test_get_order()
test_update_order()
test_delete_order()
test_get_orders()
