{
  "cwlVersion": "v1.0",
  "$graph": [
    {
      "class": "Workflow",
      "id": "#main",
      "label": "A Label!",
      "inputs": {
        "input_file": "File",
        "template_file": "File"
      },
      "outputs": {
        "output_file": {
          "type": "File",
          "outputSource": "hello-world/output"
        }
      },
      "steps": {
        "hello-world": {
          "run": "#tool",
          "in": {
            "input_file": "input_file",
            "template_file": "template_file"
          },
          "out": ["output"]
        },
        "hello-world2": {
          "run": "#tool",
          "in": {
            "input_file": "input_file",
            "template_file": "template_file"
          },
          "out": ["output"]
        }
      }
    },
    {
      "class": "CommandLineTool",
      "id": "#tool",
      "inputs": [],
      "outputs": []
    }
  ]
}
