FROM python:3.11-slim
WORKDIR /app

# Mirrors run_app.sh: pip install --upgrade pip
RUN pip install --upgrade pip

# Mirrors run_app.sh: pip install -r requirements.txt
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 8088

# Mirrors run_app.sh: python3 main.py
CMD ["python3", "main.py"]