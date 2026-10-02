import concurrent.futures
import requests

URL = "http://localhost:8080/shows/5/reserve"
TOTAL_REQUESTS = 100

SEATS = ["A1", "A2", "A3", "A4", "A5"]


def reserve_seat(index):
    headers = {
        "Authorization": "Bearer user-123",
        "Idempotency-Key": "per-user-burst-" + str(index),
        "Content-Type": "application/json"
    }

    body = {
        "seats": [SEATS[index % len(SEATS)]]
    }

    response = requests.post(
        URL,
        headers=headers,
        json=body
    )

    return response.status_code


with concurrent.futures.ThreadPoolExecutor(
        max_workers=TOTAL_REQUESTS) as executor:

    results = list(
        executor.map(reserve_seat, range(TOTAL_REQUESTS))
    )


success = results.count(201)
conflict = results.count(409)
other = len(results) - success - conflict

print("Total requests :", TOTAL_REQUESTS)
print("201 Created    :", success)
print("409 Conflict   :", conflict)
print("Other responses:", other)