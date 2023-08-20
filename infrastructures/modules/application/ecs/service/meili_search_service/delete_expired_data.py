# importing the requests library
from urllib import response
import requests
import time
import json

# api-endpoint
URL = "http://localhost"
MASTER_KEY = "${MEILISEARCH_MASTERKEY}"
HEADERS = {'Content-type': 'application/json', 'Authorization': "Bearer " + MASTER_KEY}
LIMIT = 10

def getIndexes():
  indexes = []
  count = 0

  while True:
    getIndexParams = {'offset': count, "limit": LIMIT}
    response = requests.get(url=URL + "/indexes", params=getIndexParams, headers=HEADERS)
    data = response.json()
    results = data["results"]
    count += len(results)
    total = int(data["total"])
    for result in results:
      indexes.append(result["uid"])
    if count >= total:
      break

  print("Found total " + str(count) + " indexes: ", indexes)
  return indexes

def getExpItems(index, expTime):
  uids = []
  count = 0

  while True:
    search_data = {
      "q": "",
      "attributesToRetrieve": ["id"],
      "filter": [
        "ttl <= " + str(expTime),
        "ttl != 0"
      ],
      "offset": count,
      "limit": LIMIT
    }
    response = requests.post(url=URL + "/indexes/" + index + "/search", headers=HEADERS, data=json.dumps(search_data))
    data = response.json()
    total = int(data["estimatedTotalHits"])
    results = data["hits"]
    for result in results:
      uids.append(result["id"])
    count += len(results)
    if count >= total:
      break

  print("Index: ", index, " found total " + str(count) + " items")
  return uids

def batchDeleteExpItems(index, items):
  print("Going to delete all exp items: ", items)
  response = requests.post(url=URL + "/indexes/" + index + "/documents/delete-batch", headers=HEADERS, data=json.dumps(items))
  return response.json()

# Get all exp items and delete them
current_time = int(time.time())
print(current_time)
indexes = getIndexes()
for index in indexes:
  expItems = getExpItems(index, current_time)
  if len(expItems) > 0:
    batchDeleteExpItems(index, expItems)
