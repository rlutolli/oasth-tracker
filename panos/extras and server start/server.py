from flask import Flask, jsonify
import get_arrivals
import search_stop

app = Flask(__name__)

@app.route('/search/<stop_code>', methods=['GET'])
def resolve_stop(stop_code):
    try:
        internal_id = search_stop.search_stop(stop_code)
        if internal_id:
            return jsonify({
                "status": "success", 
                "stop_code": stop_code,
                "internal_id": internal_id
            }), 200
        else:
            return jsonify({
                "status": "error", 
                "message": "Stop not found"
            }), 404
    except Exception as e:
        return jsonify({
            "status": "error", 
            "message": str(e)
        }), 500

@app.route('/arrivals/<int:stop_id>', methods=['GET'])
def fetch_arrivals(stop_id):
    try:
        # Use the existing robust function
        data = get_arrivals.get_arrivals(stop_id)
        
        if data is not None:
            return jsonify({
                "status": "success", 
                "stop_id": stop_id,
                "arrivals": data
            }), 200
        else:
            return jsonify({
                "status": "error", 
                "message": "Failed to retrieve data or timeout."
            }), 500
            
    except Exception as e:
        return jsonify({
            "status": "error", 
            "message": str(e)
        }), 500

@app.route('/', methods=['GET'])
def index():
    return "<h1>OASTH Proxy Server is Running</h1><p>Use /arrivals/STOP_ID to get data.</p>"

if __name__ == '__main__':
    # Run on all interfaces so phone can connect
    # Port 5000 is standard
    print("Starting Flask server on 0.0.0.0:5000...")
    app.run(host='0.0.0.0', port=5000)
