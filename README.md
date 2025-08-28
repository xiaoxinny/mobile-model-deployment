# Mobile Model Deployment

With the ever-increasingly performant mobile models that allow AI models to be embedded directly within devices itself, applications that have AI capabilities on-device are increasing appealing to the development market.

There are many use cases for such models, particularly using models that are capable of the following:

- Computer Vision (e.g. object detection, instance segmentation, etc.)
- Natural Language Processing (e.g. LLMs)
- Video Processing (e.g. video to language frameworks)
- Machine Translation

And many more.

## Models used

Given the wide capability of models nowadays, the following are mainly tested for the respective capabilities.

### CV Models

- **YOLOv8 to YOLOv12 TFLite models**
- **SmolVLM**

### Language Models

- **PaliGemma**
- **Gemma**

## Frameworks used

The applications for testing are mainly developed using **Kotlin** with **Jetpack Compose**. 

There are future plans of porting over to **Kotlin Multiplatform**, but for the sake of compatibility with native Android, Kotlin is preferred.

In terms of libraries used, the following are the main ones:

- **TensorFlow Lite**
- **Google MLKit**
